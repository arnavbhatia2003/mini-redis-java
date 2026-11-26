/*
 * Script for the analytics page.
 *
 * Once a second it asks the web server for the requests it has answered since the last
 * time (/api/stats?after=N) and redraws the tiles, charts and tables from them. Nothing
 * here is made up in the browser: if no request reached the server, the page stays empty.
 *
 * The benchmark at the bottom sends real requests from this page and compares the times.
 */
'use strict';

var STATES = ['HIT', 'MISS', 'OFF', 'DOWN'];
var STATE_LABEL = { HIT: 'HIT, from cache', MISS: 'MISS, stored after', OFF: 'Cache off', DOWN: 'Cache down' };
var STATE_COLOUR = { HIT: '#2a78d6', MISS: '#eb6834', OFF: '#1baf7a', DOWN: '#d03b3b' };
var CATEGORY_SLUGS = ['backpack', 'notebook', 'water-bottle', 'desk-lamp', 'headphones',
  'keyboard', 'wall-clock', 'coffee-mug', 'phone-case', 'umbrella'];
var PRODUCT_COUNT = 200;
var MAX_DOTS = 3000;

var log = [];           // every request we know about: {n, t, page, state, ms}
var lastNumber = 0;     // number of the newest request we have
var serverStarted = 0;
var clockOffset = 0;    // server clock minus this browser's clock, in ms
var rangeSeconds = 60;
var paused = false;
var charts = {};        // what each chart needs to answer "what is under the pointer?"

function byId(id) { return document.getElementById(id); }

/* ---------- numbers ---------- */

function sortedCopy(values) { return values.slice().sort(function (a, b) { return a - b; }); }

// the value below which the given share of the sorted values lie (0.5 = median)
function percentile(sorted, share) {
  if (!sorted.length) { return 0; }
  var position = (sorted.length - 1) * share;
  var low = Math.floor(position);
  var high = Math.ceil(position);
  return sorted[low] + (sorted[high] - sorted[low]) * (position - low);
}

function average(values) {
  if (!values.length) { return 0; }
  var sum = 0;
  for (var i = 0; i < values.length; i++) { sum += values[i]; }
  return sum / values.length;
}

function ms(value) {
  if (value >= 100) { return value.toFixed(0) + ' ms'; }
  if (value >= 10) { return value.toFixed(1) + ' ms'; }
  return value.toFixed(2) + ' ms';
}

function whole(n) { return Math.round(n).toLocaleString('en-IN'); }
function clock(t) { return new Date(t).toLocaleTimeString('en-GB'); }

// a round number at or above the value, for the top of an axis: 37 -> 50, 180 -> 200
function niceMax(value) {
  if (value <= 0) { return 1; }
  var power = Math.pow(10, Math.floor(Math.log10(value)));
  var n = value / power;
  var nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
  return nice * power;
}

function axisTicks(max, wholeNumbers) {
  var parts = 4;
  if (wholeNumbers && max % 4 !== 0) { parts = max % 2 === 0 ? 2 : 1; }
  var ticks = [];
  for (var i = 0; i <= parts; i++) { ticks.push(max * i / parts); }
  return ticks;
}

function kindOf(page) {
  if (page.indexOf('/api/home') === 0) { return 'Home page'; }
  if (page.indexOf('/api/products') === 0) { return 'Product lists'; }
  return 'Product pages';
}

/* ---------- talking to the server ---------- */

function setLive(state, text) {
  byId('live').dataset.on = String(state);
  byId('live-text').textContent = text;
}

async function poll() {
  try {
    var more = true;
    while (more) {
      var response = await fetch('/api/stats?after=' + lastNumber, { cache: 'no-store' });
      var data = await response.json();
      if (serverStarted !== 0 && data.started !== serverStarted) {
        // the web server was restarted: forget the old requests and start again from zero
        log = [];
        lastNumber = 0;
        serverStarted = data.started;
        continue;
      }
      serverStarted = data.started;
      clockOffset = data.now - Date.now();
      for (var i = 0; i < data.requests.length; i++) {
        var r = data.requests[i];
        log.push({ n: r[0], t: r[1], page: r[2], state: r[3], ms: r[4] });
        lastNumber = r[0];
      }
      more = data.requests.length >= 5000;
    }
    if (log.length > 20000) { log = log.slice(log.length - 20000); }
    setLive(!paused, paused ? 'Paused' : 'Live, updated ' + clock(Date.now()));
  } catch (error) {
    setLive('lost', 'No answer from the web server');
  }
  if (!paused) { render(); }
  setTimeout(poll, 1000);
}

/* ---------- tooltip ---------- */

function showTip(x, y, html) {
  var tip = byId('tt');
  tip.innerHTML = html;
  tip.hidden = false;
  var box = tip.getBoundingClientRect();
  var left = Math.min(window.innerWidth - box.width - 8, Math.max(8, x + 14));
  var top = y - box.height - 12;
  if (top < 8) { top = y + 16; }
  tip.style.left = left + 'px';
  tip.style.top = top + 'px';
}

function hideTip() { byId('tt').hidden = true; }

function tipRow(state, value) {
  return '<div class="tt-row"><span class="key" style="background:' + STATE_COLOUR[state] + '"></span><b>'
    + value + '</b><span>' + STATE_LABEL[state] + '</span></div>';
}

/* ---------- drawing helpers ---------- */

// a column whose top corners are rounded and whose bottom is square
function roundedTop(x, y, w, h, r) {
  r = Math.min(r, w / 2, h);
  return 'M' + x + ' ' + (y + h) + 'V' + (y + r) + 'Q' + x + ' ' + y + ' ' + (x + r) + ' ' + y
    + 'H' + (x + w - r) + 'Q' + (x + w) + ' ' + y + ' ' + (x + w) + ' ' + (y + r) + 'V' + (y + h) + 'Z';
}

function emptyChart(el, words) {
  el.innerHTML = '<div class="empty">' + words + '</div>';
}

function yAxis(L, T, plotW, plotH, max, wholeNumbers, unit) {
  var html = '';
  axisTicks(max, wholeNumbers).forEach(function (tick) {
    var y = T + plotH - plotH * tick / max;
    html += '<line class="' + (tick === 0 ? 'axisline' : 'gridline') + '" x1="' + L + '" x2="' + (L + plotW) + '" y1="' + y + '" y2="' + y + '"/>'
      + '<text x="' + (L - 8) + '" y="' + (y + 4) + '" text-anchor="end">' + whole(tick) + (unit && tick === max ? unit : '') + '</text>';
  });
  return html;
}

function xAxis(L, T, plotW, plotH, from, to) {
  var html = '';
  var labels = plotW > 700 ? 6 : 4;
  for (var i = 0; i <= labels; i++) {
    var x = L + plotW * i / labels;
    var anchor = i === 0 ? 'start' : i === labels ? 'end' : 'middle';
    html += '<text x="' + x + '" y="' + (T + plotH + 17) + '" text-anchor="' + anchor + '">' + clock(from + (to - from) * i / labels) + '</text>';
  }
  return html;
}

function sparkline(el, values) {
  var w = el.clientWidth || 160;
  var h = 26;
  var max = Math.max.apply(null, values.concat([1]));
  if (values.length < 2) { el.innerHTML = ''; return; }
  var points = values.map(function (v, i) {
    return [(w - 6) * i / (values.length - 1) + 3, h - 3 - (h - 6) * v / max];
  });
  var line = points.map(function (p, i) { return (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1); }).join('');
  var last = points[points.length - 1];
  el.innerHTML = '<svg viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="none"><g class="sketchy">'
    + '<path class="sparkarea" d="' + line + 'L' + last[0] + ' ' + h + 'L3 ' + h + 'Z"/>'
    + '<path class="sparkline" d="' + line + '"/></g>'
    + '<circle class="sparkdot" cx="' + last[0] + '" cy="' + last[1] + '" r="3.5"/></svg>';
}

/* ---------- the two big charts ---------- */

function drawRequests(buckets, from, to, bucketMs) {
  var el = byId('chart-requests');
  var most = 0;
  buckets.forEach(function (b) { most = Math.max(most, b.total); });
  if (!most) {
    charts.requests = null;
    emptyChart(el, 'No requests in this range yet. Click around the shop, or run a benchmark below or in the terminal.');
    return;
  }
  var W = el.clientWidth, H = el.clientHeight;
  var L = 44, R = 10, T = 10, B = 24;
  var plotW = W - L - R, plotH = H - T - B;
  var max = niceMax(most);
  var band = plotW / buckets.length;
  var barW = Math.max(1, Math.min(24, band - 2));

  var marks = '';
  buckets.forEach(function (b, i) {
    var x = L + band * i + (band - barW) / 2;
    var y = T + plotH;
    var present = STATES.filter(function (s) { return b[s] > 0; });
    present.forEach(function (state, k) {
      var h = plotH * b[state] / max;
      var gap = k > 0 && h > 4 ? 2 : 0; // a small gap in the background colour separates the segments
      y -= h;
      var cls = 'm-' + state.toLowerCase();
      if (k === present.length - 1) {
        marks += '<path class="' + cls + '" d="' + roundedTop(x, y, barW, h - gap, 4) + '"/>';
      } else {
        marks += '<rect class="' + cls + '" x="' + x + '" y="' + y + '" width="' + barW + '" height="' + Math.max(0.5, h - gap) + '"/>';
      }
    });
  });

  el.innerHTML = '<svg viewBox="0 0 ' + W + ' ' + H + '">' + yAxis(L, T, plotW, plotH, max, true)
    + '<g class="sketchy">' + marks + '</g>' + xAxis(L, T, plotW, plotH, from, to)
    + '<line class="crosshair" id="cross-requests" y1="' + T + '" y2="' + (T + plotH) + '" visibility="hidden"/></svg>';
  charts.requests = { buckets: buckets, L: L, band: band, bucketMs: bucketMs, from: from };
}

function drawSpeed(rows, from, to) {
  var el = byId('chart-speed');
  if (!rows.length) {
    charts.speed = null;
    emptyChart(el, 'No requests in this range yet.');
    return;
  }
  var shown = rows.length > MAX_DOTS ? rows.slice(rows.length - MAX_DOTS) : rows;
  var W = el.clientWidth, H = el.clientHeight;
  var L = 52, R = 10, T = 12, B = 24;
  var plotW = W - L - R, plotH = H - T - B;
  var slowest = 0;
  shown.forEach(function (r) { slowest = Math.max(slowest, r.ms); });
  var max = niceMax(slowest);
  var radius = shown.length > 400 ? 3.2 : 4.5;
  var span = Math.max(1, to - from);

  var points = [];
  var dots = '';
  shown.forEach(function (r) {
    var x = L + plotW * (r.t - from) / span;
    var y = T + plotH - plotH * r.ms / max;
    points.push({ x: x, y: y, r: r });
    dots += '<circle class="dot d-' + r.state.toLowerCase() + '" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="' + radius + '"/>';
  });

  el.innerHTML = '<svg viewBox="0 0 ' + W + ' ' + H + '">' + yAxis(L, T, plotW, plotH, max, false, ' ms')
    + '<g class="sketchy">' + dots + '</g>' + xAxis(L, T, plotW, plotH, from, to)
    + '<circle id="ring-speed" r="' + (radius + 3.5) + '" fill="none" stroke="var(--text)" stroke-width="2" visibility="hidden"/></svg>';
  charts.speed = { points: points };
}

function hoverRequests(event) {
  var chart = charts.requests;
  if (!chart) { hideTip(); return; }
  var box = byId('chart-requests').getBoundingClientRect();
  var index = Math.floor((event.clientX - box.left - chart.L) / chart.band);
  if (index < 0 || index >= chart.buckets.length) { hideTip(); return; }
  var b = chart.buckets[index];
  var cross = byId('cross-requests');
  if (cross) {
    var x = chart.L + chart.band * (index + 0.5);
    cross.setAttribute('x1', x);
    cross.setAttribute('x2', x);
    cross.setAttribute('visibility', 'visible');
  }
  var start = chart.from + index * chart.bucketMs;
  var html = '<div class="tt-title">' + clock(start) + ' to ' + clock(start + chart.bucketMs) + '</div>';
  STATES.forEach(function (state) { if (b[state]) { html += tipRow(state, b[state]); } });
  html += '<div class="tt-row"><span class="key"></span><b>' + b.total + '</b><span>requests in all</span></div>';
  showTip(event.clientX, event.clientY, html);
}

function hoverSpeed(event) {
  var chart = charts.speed;
  if (!chart) { hideTip(); return; }
  var box = byId('chart-speed').getBoundingClientRect();
  var px = event.clientX - box.left, py = event.clientY - box.top;
  var best = null, bestDistance = 28 * 28; // the pointer only has to be near a dot, not on it
  chart.points.forEach(function (p) {
    var d = (p.x - px) * (p.x - px) + (p.y - py) * (p.y - py);
    if (d < bestDistance) { bestDistance = d; best = p; }
  });
  var ring = byId('ring-speed');
  if (!best) { hideTip(); if (ring) { ring.setAttribute('visibility', 'hidden'); } return; }
  if (ring) {
    ring.setAttribute('cx', best.x);
    ring.setAttribute('cy', best.y);
    ring.setAttribute('visibility', 'visible');
  }
  var tip = document.createElement('div');
  var title = document.createElement('div');
  title.className = 'tt-title';
  title.textContent = clock(best.r.t) + '  ' + best.r.page;
  tip.appendChild(title);
  showTip(event.clientX, event.clientY, tip.innerHTML + tipRow(best.r.state, ms(best.r.ms)));
}

/* ---------- tiles, tables, lists ---------- */

function statePill(state) {
  return '<span class="pill"><i style="background:' + (STATE_COLOUR[state] || '#8a93a6') + '"></i>' + state + '</span>';
}

function render() {
  var now = Date.now() + clockOffset;
  var from = rangeSeconds ? now - rangeSeconds * 1000 : (serverStarted || now);
  var rows = log.filter(function (r) { return r.t >= from; });

  // slices of time for the charts: about 60 of them, each at least one second wide
  var span = Math.max(1000, now - from);
  var bucketMs = Math.max(1000, Math.ceil(span / 60 / 1000) * 1000);
  var bucketCount = Math.max(1, Math.ceil(span / bucketMs));
  var buckets = [];
  for (var i = 0; i < bucketCount; i++) { buckets.push({ HIT: 0, MISS: 0, OFF: 0, DOWN: 0, total: 0, times: [] }); }

  var times = [], hitTimes = [], slowTimes = [];
  var counts = { HIT: 0, MISS: 0, OFF: 0, DOWN: 0 };
  var kinds = {}, pages = {};
  rows.forEach(function (r) {
    var b = buckets[Math.min(bucketCount - 1, Math.max(0, Math.floor((r.t - from) / bucketMs)))];
    if (b[r.state] !== undefined) { b[r.state]++; }
    b.total++;
    b.times.push(r.ms);

    times.push(r.ms);
    if (counts[r.state] !== undefined) { counts[r.state]++; }
    if (r.state === 'HIT') { hitTimes.push(r.ms); } else { slowTimes.push(r.ms); }

    var kind = kindOf(r.page);
    if (!kinds[kind]) { kinds[kind] = { requests: 0, hit: 0, asked: 0, hitTimes: [], slowTimes: [] }; }
    kinds[kind].requests++;
    if (r.state !== 'OFF') { kinds[kind].asked++; }
    if (r.state === 'HIT') { kinds[kind].hit++; kinds[kind].hitTimes.push(r.ms); } else { kinds[kind].slowTimes.push(r.ms); }
    pages[r.page] = (pages[r.page] || 0) + 1;
  });

  var sorted = sortedCopy(times);
  var medianHit = percentile(sortedCopy(hitTimes), 0.5);
  var medianSlow = percentile(sortedCopy(slowTimes), 0.5);
  var asked = counts.HIT + counts.MISS + counts.DOWN; // requests that tried the cache

  byId('range-note').textContent = whole(rows.length) + ' requests in this range, ' + whole(log.length)
    + ' since the web server started at ' + (serverStarted ? clock(serverStarted - clockOffset) : '...');

  byId('t-requests').textContent = whole(rows.length);
  var lastTen = rows.filter(function (r) { return r.t >= now - 10000; }).length;
  byId('t-requests-sub').textContent = (lastTen / 10).toFixed(1) + ' per second over the last 10 s';
  sparkline(byId('s-requests'), buckets.map(function (b) { return b.total; }));

  byId('t-ratio').textContent = asked ? Math.round(100 * counts.HIT / asked) + '%' : '–';
  byId('t-ratio-sub').textContent = asked ? whole(counts.HIT) + ' HIT of ' + whole(asked) + ' that asked the cache' : 'no request asked the cache yet';
  byId('m-ratio').style.width = (asked ? 100 * counts.HIT / asked : 0) + '%';

  byId('t-median').textContent = sorted.length ? ms(percentile(sorted, 0.5)) : '–';
  byId('t-median-sub').textContent = (hitTimes.length ? 'HIT ' + ms(medianHit) : 'no HIT yet')
    + (slowTimes.length ? ', not cached ' + ms(medianSlow) : '');
  sparkline(byId('s-median'), buckets.map(function (b) { return percentile(sortedCopy(b.times), 0.5); }));

  byId('t-p95').textContent = sorted.length ? ms(percentile(sorted, 0.95)) : '–';
  byId('t-p95-sub').textContent = sorted.length ? 'slowest ' + ms(sorted[sorted.length - 1]) : '';

  if (hitTimes.length && slowTimes.length) {
    var saved = hitTimes.length * Math.max(0, medianSlow - medianHit) / 1000;
    byId('t-saved').textContent = saved >= 10 ? saved.toFixed(0) + ' s' : saved.toFixed(1) + ' s';
    byId('t-saved-sub').textContent = whole(hitTimes.length) + ' HITs x (' + ms(medianSlow) + ' - ' + ms(medianHit) + ')';
  } else {
    byId('t-saved').textContent = '–';
    byId('t-saved-sub').textContent = 'needs a HIT and a non-cached request';
  }

  drawRequests(buckets, from, now, bucketMs);
  drawSpeed(rows, from, now);

  // table: by kind of page
  var kindRows = '';
  ['Home page', 'Product lists', 'Product pages'].forEach(function (name) {
    var k = kinds[name];
    if (!k) {
      kindRows += '<tr><td>' + name + '</td><td class="num muted">0</td><td class="muted">–</td><td class="num muted">–</td><td class="num muted">–</td></tr>';
      return;
    }
    var share = k.asked ? Math.round(100 * k.hit / k.asked) : null;
    kindRows += '<tr><td>' + name + '</td><td class="num">' + whole(k.requests) + '</td><td>'
      + (share === null ? '<span class="muted">cache off</span>'
        : '<div class="share"><div class="share-track"><div class="share-fill" style="width:' + share + '%"></div></div><span>' + share + '%</span></div>')
      + '</td><td class="num">' + (k.slowTimes.length ? ms(percentile(sortedCopy(k.slowTimes), 0.5)) : '–')
      + '</td><td class="num">' + (k.hitTimes.length ? ms(percentile(sortedCopy(k.hitTimes), 0.5)) : '–') + '</td></tr>';
  });
  byId('table-kinds').tBodies[0].innerHTML = kindRows;

  // list: most requested pages
  var top = Object.keys(pages).map(function (p) { return [p, pages[p]]; })
    .sort(function (a, b) { return b[1] - a[1] || (a[0] < b[0] ? -1 : 1); }).slice(0, 8);
  var list = byId('top-pages');
  list.textContent = '';
  if (!top.length) { list.innerHTML = '<p class="muted">No requests in this range yet.</p>'; }
  top.forEach(function (entry) {
    var row = document.createElement('div');
    row.className = 'rank-row';
    var path = document.createElement('span');
    path.className = 'path';
    path.textContent = entry[0];
    var n = document.createElement('span');
    n.className = 'n';
    n.textContent = whole(entry[1]);
    var bar = document.createElement('div');
    bar.className = 'bar';
    bar.style.width = (100 * entry[1] / top[0][1]) + '%';
    row.appendChild(path);
    row.appendChild(n);
    row.appendChild(bar);
    list.appendChild(row);
  });

  // table: newest requests
  var body = byId('table-log').tBodies[0];
  body.textContent = '';
  rows.slice(-40).reverse().forEach(function (r) {
    var tr = body.insertRow();
    tr.insertCell().textContent = clock(r.t);
    var path = tr.insertCell();
    path.className = 'path';
    path.textContent = r.page;
    tr.insertCell().innerHTML = statePill(r.state);
    var time = tr.insertCell();
    time.className = 'num';
    time.textContent = ms(r.ms);
  });
  if (!rows.length) {
    var tr = body.insertRow();
    var cell = tr.insertCell();
    cell.colSpan = 4;
    cell.className = 'muted';
    cell.textContent = 'No requests in this range yet.';
  }
}

/* ---------- benchmark: send real requests from this page ---------- */

var bench = { running: false, stop: false, history: [] };

// The pages one run may visit. Product pages are picked at random, different every run.
function choosePages(home, lists, productPages) {
  var pages = [];
  if (home) { pages.push('/api/home?page=home'); }
  if (lists) {
    pages.push('/api/products?category=all');
    CATEGORY_SLUGS.forEach(function (slug) { pages.push('/api/products?category=' + slug); });
  }
  var ids = [];
  for (var id = 1; id <= PRODUCT_COUNT; id++) { ids.push(id); }
  for (var i = 0; i < Math.min(productPages, PRODUCT_COUNT); i++) {
    var pick = Math.floor(Math.random() * ids.length);
    pages.push('/api/product?id=' + ids.splice(pick, 1)[0]);
  }
  return pages;
}

// Sends every visit once, with the given number of "users" working at the same time.
async function runOnce(visits, mode, users, tick) {
  var results = [];
  var next = 0;
  async function user() {
    while (!bench.stop) {
      var i = next++;
      if (i >= visits.length) { return; }
      var started = performance.now();
      try {
        var response = await fetch(visits[i] + '&cache=' + mode, { cache: 'no-store' });
        await response.text();
        if (response.ok) {
          results.push({ state: response.headers.get('X-Cache'), server: parseFloat(response.headers.get('X-Time-Ms')), ms: performance.now() - started });
        } else {
          results.push({ failed: true });
        }
      } catch (error) {
        results.push({ failed: true });
      }
      tick();
    }
  }
  var began = performance.now();
  var team = [];
  for (var u = 0; u < users; u++) { team.push(user()); }
  await Promise.all(team);
  return summarise(results, (performance.now() - began) / 1000);
}

function summarise(results, seconds) {
  var good = results.filter(function (r) { return !r.failed; });
  var times = sortedCopy(good.map(function (r) { return r.ms; }));
  var counts = { HIT: 0, MISS: 0, OFF: 0, DOWN: 0 };
  good.forEach(function (r) { if (counts[r.state] !== undefined) { counts[r.state]++; } });
  return {
    requests: good.length,
    failed: results.length - good.length,
    average: average(times),
    median: percentile(times, 0.5),
    p95: percentile(times, 0.95),
    slowest: times.length ? times[times.length - 1] : 0,
    server: average(good.map(function (r) { return r.server; })),
    perSecond: seconds > 0 ? good.length / seconds : 0,
    counts: counts
  };
}

function benchStatus(text) { byId('b-status').textContent = text; }

async function runBenchmark(event) {
  event.preventDefault();
  if (bench.running) { return; }
  var requests = Math.max(10, Math.min(2000, parseInt(byId('b-requests').value, 10) || 100));
  var users = Math.max(1, Math.min(6, parseInt(byId('b-users').value, 10) || 4));
  var productPages = Math.max(0, Math.min(200, parseInt(byId('b-products').value, 10) || 0));
  var mode = byId('b-mode').value;
  byId('b-requests').value = requests;
  byId('b-users').value = users;
  byId('b-products').value = productPages;

  var pages = choosePages(byId('b-home').checked, byId('b-lists').checked, productPages);
  if (!pages.length) { benchStatus('Choose at least one kind of page to visit.'); return; }
  var visits = [];
  for (var i = 0; i < requests; i++) { visits.push(pages[Math.floor(Math.random() * pages.length)]); }

  bench.running = true;
  bench.stop = false;
  byId('b-run').disabled = true;
  byId('b-stop').disabled = false;
  byId('b-progress').hidden = false;
  var runs = mode === 'compare' ? ['off', 'on'] : [mode];
  var done = 0;
  function tick() {
    done++;
    byId('b-progress-fill').style.width = (100 * done / (visits.length * runs.length)) + '%';
  }

  try {
    if (byId('b-clear').checked) {
      benchStatus('Clearing the cache ...');
      await fetch('/api/clear-cache', { cache: 'no-store' });
    }
    var result = { off: null, on: null };
    for (var k = 0; k < runs.length && !bench.stop; k++) {
      benchStatus('Run ' + (k + 1) + ' of ' + runs.length + ': ' + requests + ' requests ' + (runs[k] === 'off' ? 'without' : 'with')
        + ' the cache, ' + users + ' at a time, over ' + pages.length + ' different pages ...');
      result[runs[k]] = await runOnce(visits, runs[k], users, tick);
    }
    if (bench.stop) {
      benchStatus('Stopped. The requests already sent still show in the charts above.');
    } else {
      showBenchmark(result, { requests: requests, users: users, pages: pages.length });
      benchStatus('Done at ' + clock(Date.now()) + '. Times in this table are measured in the browser, from sending a request until its answer has arrived.');
    }
  } catch (error) {
    benchStatus('The benchmark stopped: ' + error.message);
  }
  bench.running = false;
  byId('b-run').disabled = false;
  byId('b-stop').disabled = true;
  byId('b-progress').hidden = true;
  byId('b-progress-fill').style.width = '0';
}

function showBenchmark(result, settings) {
  var off = result.off, on = result.on;
  byId('b-result').hidden = false;

  var faster = null;
  if (off && on && on.counts.HIT > 0 && on.average > 0 && on.average < off.average) { faster = off.average / on.average; }
  if (off && on) {
    if (faster) {
      byId('b-headline').textContent = faster.toFixed(1) + 'x';
      byId('b-caption').textContent = 'faster on average with the cache, counting its ' + on.counts.MISS + ' slow MISS'
        + (on.counts.MISS === 1 ? '' : 'es') + '.';
    } else {
      byId('b-headline').textContent = 'No speed-up';
      byId('b-caption').textContent = on.counts.DOWN ? 'mini-redis was not running, so nothing was cached.'
        : 'Every page was visited too rarely to be served from the cache.';
    }
  } else {
    var only = off || on;
    byId('b-headline').textContent = ms(only.average);
    byId('b-caption').textContent = 'average request ' + (off ? 'without' : 'with') + ' the cache. Choose "Without, then with cache" to compare.';
  }

  function cell(run, text) { return '<td class="num' + (run ? '' : ' muted') + '">' + (run ? text(run) : '–') + '</td>'; }
  function line(label, text) { return '<tr><td>' + label + '</td>' + cell(off, text) + cell(on, text) + '</tr>'; }
  var rows = line('Requests', function (r) { return whole(r.requests); })
    + line('Average', function (r) { return ms(r.average); })
    + line('Median', function (r) { return ms(r.median); })
    + line('95th percentile', function (r) { return ms(r.p95); })
    + line('Slowest', function (r) { return ms(r.slowest); })
    + line('Average time on the server', function (r) { return ms(r.server); })
    + line('Requests per second', function (r) { return r.perSecond.toFixed(1); })
    + '<tr><td>Cache HIT</td><td class="num muted">–</td>' + cell(on, function (r) { return whole(r.counts.HIT); }) + '</tr>'
    + '<tr><td>Cache MISS</td><td class="num muted">–</td>' + cell(on, function (r) { return whole(r.counts.MISS); }) + '</tr>';
  if (on && on.counts.DOWN) { rows += '<tr><td>Cache DOWN</td><td class="num muted">–</td><td class="num">' + whole(on.counts.DOWN) + '</td></tr>'; }
  if ((off && off.failed) || (on && on.failed)) { rows += line('Failed requests', function (r) { return whole(r.failed); }); }
  byId('table-bench').tBodies[0].innerHTML = rows;

  drawBenchBars(off, on);

  bench.history.unshift({ at: Date.now(), settings: settings, off: off, on: on, faster: faster });
  var body = byId('table-history').tBodies[0];
  body.innerHTML = bench.history.map(function (h) {
    return '<tr><td>' + clock(h.at) + '</td><td>' + h.settings.requests + ' requests, ' + h.settings.users + ' users, '
      + h.settings.pages + ' pages</td><td class="num">' + (h.off ? ms(h.off.average) : '–') + '</td><td class="num">'
      + (h.on ? ms(h.on.average) : '–') + '</td><td class="num">' + (h.on ? h.on.counts.HIT + ' / ' + h.on.counts.MISS : '–')
      + '</td><td class="num">' + (h.faster ? h.faster.toFixed(1) + 'x' : '–') + '</td></tr>';
  }).join('');
  byId('b-history-box').hidden = false;
}

// Horizontal bars: average, median and 95th percentile, without and with the cache.
function drawBenchBars(off, on) {
  var el = byId('chart-bench');
  var measures = [['Average', 'average'], ['Median', 'median'], ['95th percentile', 'p95']];
  var series = [];
  if (off) { series.push(['off', off]); }
  if (on) { series.push(['hit', on]); }
  var most = 0;
  measures.forEach(function (m) { series.forEach(function (s) { most = Math.max(most, s[1][m[1]]); }); });
  var W = el.clientWidth || 360;
  var labelW = 104, valueW = 74, barH = 14, groupGap = 16;
  var plotW = W - labelW - valueW;
  var groupH = series.length * (barH + 2) + groupGap;
  var H = measures.length * groupH;

  var html = '';
  measures.forEach(function (m, i) {
    var top = i * groupH + 4;
    html += '<text x="0" y="' + (top + series.length * (barH + 2) / 2 + 3) + '">' + m[0] + '</text>';
    series.forEach(function (s, k) {
      var value = s[1][m[1]];
      var w = Math.max(2, plotW * value / (most || 1));
      var y = top + k * (barH + 2);
      html += '<g class="sketchy"><path class="m-' + s[0] + '" d="M' + labelW + ' ' + y + 'H' + (labelW + w - 4) + 'Q' + (labelW + w) + ' ' + y + ' '
        + (labelW + w) + ' ' + (y + 4) + 'V' + (y + barH - 4) + 'Q' + (labelW + w) + ' ' + (y + barH) + ' ' + (labelW + w - 4) + ' ' + (y + barH)
        + 'H' + labelW + 'Z"/></g><text x="' + (labelW + w + 6) + '" y="' + (y + barH - 3) + '" style="fill:var(--text)">' + ms(value) + '</text>';
    });
  });
  el.style.height = H + 'px';
  el.innerHTML = '<svg viewBox="0 0 ' + W + ' ' + H + '">' + html + '</svg>';

  byId('legend-bench').innerHTML = (off ? '<span><i style="background:' + STATE_COLOUR.OFF + '"></i>Without cache</span>' : '')
    + (on ? '<span><i style="background:' + STATE_COLOUR.HIT + '"></i>With cache</span>' : '');
}

/* ---------- start ---------- */

function chooseTheme(theme) {
  document.documentElement.dataset.theme = theme;
  try { localStorage.setItem('cachet-theme', theme); } catch (e) { /* fine without it */ }
  document.querySelectorAll('[data-theme-choice]').forEach(function (b) {
    b.setAttribute('aria-pressed', String(b.dataset.themeChoice === theme));
  });
  render();
}

function start() {
  var legend = STATES.map(function (s) { return '<span><i style="background:' + STATE_COLOUR[s] + '"></i>' + STATE_LABEL[s] + '</span>'; }).join('');
  byId('legend-requests').innerHTML = legend;
  byId('legend-speed').innerHTML = legend.replace(/<i /g, '<i class="dot" ');

  document.querySelectorAll('[data-theme-choice]').forEach(function (b) {
    b.addEventListener('click', function () { chooseTheme(b.dataset.themeChoice); });
  });
  var saved = 'dashboard';
  try { saved = localStorage.getItem('cachet-theme') || 'dashboard'; } catch (e) { /* keep the default */ }
  chooseTheme(saved === 'sketch' ? 'sketch' : 'dashboard');

  document.querySelectorAll('#range button').forEach(function (b) {
    b.addEventListener('click', function () {
      rangeSeconds = parseInt(b.dataset.range, 10);
      document.querySelectorAll('#range button').forEach(function (other) { other.setAttribute('aria-pressed', String(other === b)); });
      render();
    });
  });

  byId('pause').addEventListener('click', function () {
    paused = !paused;
    byId('pause').textContent = paused ? 'Resume' : 'Pause';
    setLive(!paused, paused ? 'Paused' : 'Live');
    if (!paused) { render(); }
  });

  byId('clear-cache').addEventListener('click', async function () {
    var button = byId('clear-cache');
    button.disabled = true;
    try {
      var response = await fetch('/api/clear-cache', { cache: 'no-store' });
      var data = await response.json();
      button.textContent = response.ok ? 'Cleared ' + data.removed + ' stored pages' : 'mini-redis is not running';
    } catch (error) {
      button.textContent = 'No answer from the web server';
    }
    setTimeout(function () { button.textContent = 'Clear cache'; button.disabled = false; }, 2500);
  });

  byId('chart-requests').addEventListener('pointermove', hoverRequests);
  byId('chart-requests').addEventListener('pointerleave', function () {
    hideTip();
    var cross = byId('cross-requests');
    if (cross) { cross.setAttribute('visibility', 'hidden'); }
  });
  byId('chart-speed').addEventListener('pointermove', hoverSpeed);
  byId('chart-speed').addEventListener('pointerleave', function () {
    hideTip();
    var ring = byId('ring-speed');
    if (ring) { ring.setAttribute('visibility', 'hidden'); }
  });

  byId('bench-form').addEventListener('submit', runBenchmark);
  byId('b-stop').addEventListener('click', function () { bench.stop = true; });
  window.addEventListener('resize', render);

  poll();
}

start();
