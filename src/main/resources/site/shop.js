/*
 * Script for the three shop pages (home, product list, product page).
 * Every number on a page comes from the server: /api/home, /api/products, /api/product.
 * The STYLES and CATEGORIES lists are in the same order as in SalesData.java, so that
 * product number = styleIndex * 10 + categoryIndex + 1.
 */
'use strict';

// finish name -> [chip colour, colour of the drawing on it]
var STYLES = [
  ['Red', '#d93a2b', '#fff'], ['Blue', '#2456d6', '#fff'], ['Green', '#1f8a4c', '#fff'],
  ['Black', '#17181b', '#fff'], ['White', '#f6f6f3', '#17181b'], ['Silver', '#c4c8cf', '#17181b'],
  ['Golden', '#d4a72c', '#17181b'], ['Orange', '#f07a1a', '#17181b'], ['Purple', '#7447c9', '#fff'],
  ['Yellow', '#f4d23c', '#17181b'], ['Small', '#9fd3c7', '#17181b'], ['Large', '#24406b', '#fff'],
  ['Classic', '#8a5a3b', '#fff'], ['Smart', '#0f8f9e', '#fff'], ['Mini', '#f2a7c3', '#17181b'],
  ['Travel', '#c9b38a', '#17181b'], ['Office', '#5a6270', '#fff'], ['Budget', '#b7c45a', '#17181b'],
  ['Premium', '#3a1f3d', '#fff'], ['Everyday', '#e2725b', '#17181b']
];

var CATEGORIES = ['Backpack', 'Notebook', 'Water Bottle', 'Desk Lamp', 'Headphones',
  'Keyboard', 'Wall Clock', 'Coffee Mug', 'Phone Case', 'Umbrella'];

// one line drawing per kind of object, on a 64 x 64 canvas
var ICONS = {
  'Backpack': '<path d="M26 20v-4a6 6 0 0 1 12 0v4"/><rect x="15" y="20" width="34" height="36" rx="9"/><rect x="22" y="38" width="20" height="12" rx="3"/><path d="M22 30h20"/>',
  'Notebook': '<rect x="17" y="9" width="32" height="46" rx="3"/><path d="M24 9v46M31 21h11M31 29h11"/><path d="M13 18h7M13 28h7M13 38h7M13 48h7"/>',
  'Water Bottle': '<rect x="26" y="7" width="12" height="7" rx="2"/><path d="M26 14v4c0 3-5 5-5 10v25a4 4 0 0 0 4 4h14a4 4 0 0 0 4-4V28c0-5-5-7-5-10v-4"/><path d="M21 36h22"/>',
  'Desk Lamp': '<path d="M18 56h28M32 56V36M32 36L20 22"/><path d="M11 26l11-15 14 9z"/><circle cx="32" cy="36" r="2.5"/>',
  'Headphones': '<path d="M13 38v-5a19 19 0 0 1 38 0v5"/><rect x="9" y="35" width="11" height="18" rx="4.5"/><rect x="44" y="35" width="11" height="18" rx="4.5"/>',
  'Keyboard': '<rect x="7" y="20" width="50" height="26" rx="5"/><path d="M15 28h2M23 28h2M31 28h2M39 28h2M47 28h2M15 34h2M47 34h2M22 39h20"/>',
  'Wall Clock': '<circle cx="32" cy="32" r="23"/><path d="M32 18v14l9 6"/><path d="M32 9v3M32 52v3M9 32h3M52 32h3"/>',
  'Coffee Mug': '<path d="M13 22h30v18a12 12 0 0 1-12 12h-6a12 12 0 0 1-12-12z"/><path d="M43 26h4a6 6 0 0 1 0 12h-4"/><path d="M22 8c0 4 4 4 4 8M32 8c0 4 4 4 4 8"/>',
  'Phone Case': '<rect x="19" y="7" width="26" height="50" rx="7"/><rect x="24" y="12" width="11" height="11" rx="3.5"/><circle cx="29.5" cy="17.5" r="2"/>',
  'Umbrella': '<path d="M7 33a25 25 0 0 1 50 0z"/><path d="M32 33v17a5 5 0 0 1-10 0M32 8V5"/><path d="M23.500 33c0-10 4-19 8.500-25M40.500 33c0-10-4-19-8.500-25"/>'
};

var WORDS = {
  'Backpack': 'Carries a laptop, a lunch and a rain jacket without looking like it. Padded back, quiet zips, one pocket you can reach without taking it off.',
  'Notebook': 'Thread-bound so it opens flat, with paper heavy enough for a fountain pen. 160 pages, numbered, with an index you will actually fill in.',
  'Water Bottle': 'Double-walled steel that keeps water cold through a full day of lectures. The cap comes apart in two pieces, so it gets properly clean.',
  'Desk Lamp': 'A weighted base and one stiff joint: it stays where you point it. Warm light for the evening, brighter for close work.',
  'Headphones': 'Closed-back cups that shut out a hostel corridor. Soft pads you can replace, and a cable that unplugs instead of breaking.',
  'Keyboard': 'Low keys with a firm, short press and a spacebar that does not rattle. Pairs with three devices and remembers all of them.',
  'Wall Clock': 'A silent sweep movement behind a domed lens. One AA cell lasts about a year, and you can read it from across the room.',
  'Coffee Mug': 'Stoneware with a wide handle and a base that will not tip. Holds 350 ml and goes in the microwave and the dishwasher.',
  'Phone Case': 'A raised edge around the screen and the camera, and buttons that still click. Thin enough that you forget it is there.',
  'Umbrella': 'Eight fibreglass ribs that flex in the wind instead of turning inside out. Opens with one hand, dries fast, folds to 28 cm.'
};

var FINISH_NOTE = {
  'Small': 'The compact size.', 'Large': 'The roomier size.', 'Classic': 'The original design, unchanged.',
  'Smart': 'The one with the extra features.', 'Mini': 'The smallest we make.', 'Travel': 'Built to live in a bag.',
  'Office': 'Made for a desk, nine to five.', 'Budget': 'The same idea at the lowest price.',
  'Premium': 'Our best materials.', 'Everyday': 'The one most people should buy.'
};

/* ---------- small helpers ---------- */

function byId(id) { return document.getElementById(id); }

function esc(text) {
  return String(text).replace(/[&<>"']/g, function (ch) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
  });
}

function rupees(n) { return '₹' + Number(n).toLocaleString('en-IN'); }
function count(n) { return Number(n).toLocaleString('en-IN'); }
function slugOf(category) { return category.toLowerCase().replace(/ /g, '-'); }
function number3(id) { return 'No. ' + String(id).padStart(3, '0'); }

function shortRupees(n) {
  if (n >= 1e7) { return '₹' + (n / 1e7).toFixed(1) + ' cr'; }
  if (n >= 1e5) { return '₹' + (n / 1e5).toFixed(1) + ' lakh'; }
  return rupees(n);
}

function ordinal(n) {
  var tens = n % 100;
  if (tens >= 11 && tens <= 13) { return n + 'th'; }
  return n + (['th', 'st', 'nd', 'rd'][n % 10] || 'th');
}

function styleOf(name) {
  for (var i = 0; i < STYLES.length; i++) {
    if (STYLES[i][0] === name) { return STYLES[i]; }
  }
  return ['', '#e4e5e9', '#17181b'];
}

function isPale(colour) { return colour === '#f6f6f3'; }

function drawing(category) {
  return '<svg viewBox="0 0 64 64" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
    + (ICONS[category] || '<rect x="14" y="14" width="36" height="36" rx="4"/>') + '</svg>';
}

/* ---------- cache switch and the request to the server ---------- */

function cacheMode() {
  try { return localStorage.getItem('cachet-cache') === 'off' ? 'off' : 'on'; } catch (e) { return 'on'; }
}

function setCacheMode(mode) {
  try { localStorage.setItem('cachet-cache', mode); } catch (e) { /* the page still works without it */ }
}

var PERF_WORDS = {
  HIT: 'answer came from mini-redis',
  MISS: 'read all orders, then stored the answer',
  OFF: 'cache off: read all orders',
  DOWN: 'mini-redis is not running: read all orders'
};

function showPerf(state, serverMs) {
  var chip = byId('perf');
  if (!chip) { return; }
  chip.dataset.state = state;
  chip.innerHTML = '<i></i><b>' + esc(state) + ' · ' + serverMs.toFixed(serverMs < 10 ? 2 : 0) + ' ms</b><span>'
    + esc(PERF_WORDS[state] || '') + '</span>';
}

// Asks the server for data. Adds cache=on or cache=off from the switch in the top bar.
async function api(path) {
  var url = path + (path.indexOf('?') < 0 ? '?' : '&') + 'cache=' + cacheMode();
  var response = await fetch(url, { cache: 'no-store' });
  var data = await response.json();
  if (!response.ok) { throw new Error(data.error || 'the server answered ' + response.status); }
  showPerf(response.headers.get('X-Cache') || '?', parseFloat(response.headers.get('X-Time-Ms')) || 0);
  return data;
}

/* ---------- parts shared by every page ---------- */

function renderChrome(currentSlug) {
  var cats = CATEGORIES.map(function (c) {
    var slug = slugOf(c);
    return '<a href="/products?category=' + slug + '"' + (slug === currentSlug ? ' aria-current="page"' : '') + '>' + esc(c) + '</a>';
  }).join('');

  byId('site-header').innerHTML =
    '<div class="topbar"><div class="wrap">'
    + '<span class="mono">200 objects · every figure counted live from 400,000 orders</span>'
    + '<div class="topbar-tools">'
    + '<div class="switch" role="group" aria-label="Cache">'
    + '<button type="button" id="cache-on">Cache on</button><button type="button" id="cache-off">Cache off</button></div>'
    + '<a class="tool" href="/analytics" id="open-analytics">Analytics ↗</a>'
    + '</div></div></div>'
    + '<div class="masthead"><div class="wrap">'
    + '<a class="wordmark" href="/">Cachet<small>Objects in twenty finishes</small></a>'
    + '<nav class="mainnav" aria-label="Main">'
    + '<a href="/products"' + (currentSlug === 'all' ? ' aria-current="page"' : '') + '>Shop all</a>'
    + '<a href="/#bestsellers">Bestsellers</a><a href="/#how">How it stays fast</a></nav>'
    + '</div></div>'
    + '<nav class="catnav" aria-label="Categories"><div class="wrap">' + cats + '</div></nav>';

  byId('site-footer').innerHTML =
    '<div class="footer"><div class="wrap">'
    + '<div><a class="wordmark" href="/">Cachet</a>'
    + '<p>A demo shop for a college project. Nothing here is for sale. The sales figures come from a generated file of 400,000 orders, and the cache behind the pages is a small Redis-like server written in Java.</p></div>'
    + '<div><h4>Shop</h4><ul><li><a href="/products">All products</a></li>'
    + CATEGORIES.slice(0, 5).map(function (c) { return '<li><a href="/products?category=' + slugOf(c) + '">' + esc(c) + '</a></li>'; }).join('')
    + '</ul></div>'
    + '<div><h4>Behind the pages</h4><ul><li><a href="/analytics" target="cachet-analytics">Analytics and benchmark</a></li>'
    + '<li><a href="/#how">How the cache works</a></li></ul></div>'
    + '</div></div>'
    + '<a class="perf" id="perf" href="/analytics" target="cachet-analytics" title="Open analytics"><i></i><b>Waiting for the server</b></a>';

  function paintSwitch() {
    byId('cache-on').setAttribute('aria-pressed', String(cacheMode() === 'on'));
    byId('cache-off').setAttribute('aria-pressed', String(cacheMode() === 'off'));
  }
  byId('cache-on').addEventListener('click', function () { setCacheMode('on'); location.reload(); });
  byId('cache-off').addEventListener('click', function () { setCacheMode('off'); location.reload(); });
  paintSwitch();

  // the analytics page opens in its own window, next to the shop
  byId('open-analytics').addEventListener('click', function (event) {
    var opened = window.open('/analytics', 'cachet-analytics', 'width=1280,height=860');
    if (opened) { event.preventDefault(); }
  });
}

function chip(p, extra) {
  var style = styleOf(p.style);
  return '<div class="chip' + (isPale(style[1]) ? ' pale' : '') + '" style="--c:' + style[1] + ';--f:' + style[2] + '">'
    + '<span class="no mono">' + number3(p.id) + '</span>' + (extra || '') + drawing(p.category) + '</div>';
}

function card(p) {
  var badge = p.rank <= 10 ? '<span class="rank mono">Top ' + p.rank + '</span>' : '';
  return '<a class="card" href="/product?id=' + p.id + '">' + chip(p, badge)
    + '<div class="caption"><h3>' + esc(p.name) + '</h3>'
    + '<div class="row"><span class="price">' + rupees(p.price) + '</span>'
    + '<span class="sold">' + count(p.unitsSold) + ' sold</span></div></div></a>';
}

function skeletonCards(n) {
  var html = '';
  for (var i = 0; i < n; i++) {
    html += '<div class="card skeleton"><div class="chip"></div><div class="bar"></div><div class="bar short"></div></div>';
  }
  return html;
}

function showProblem(box, error) {
  box.innerHTML = '<div class="notice"><h2>This page could not be loaded</h2><p>' + esc(error.message)
    + '. Check that DemoApp is still running in its terminal, then reload.</p></div>';
}

/* ---------- home page ---------- */

function renderMatrix() {
  var html = '';
  // one row per kind of object, one column per finish: 200 small squares, each a product
  for (var c = 0; c < CATEGORIES.length; c++) {
    for (var s = 0; s < STYLES.length; s++) {
      var id = s * 10 + c + 1;
      html += '<a href="/product?id=' + id + '" style="--c:' + STYLES[s][1] + '"'
        + (isPale(STYLES[s][1]) ? ' class="pale"' : '')
        + ' data-name="' + esc(STYLES[s][0] + ' ' + CATEGORIES[c]) + '" data-no="' + number3(id) + '"'
        + ' aria-label="' + esc(STYLES[s][0] + ' ' + CATEGORIES[c]) + '"></a>';
    }
  }
  var matrix = byId('matrix');
  matrix.innerHTML = html;

  var tip = document.createElement('div');
  tip.className = 'tip';
  tip.hidden = true;
  document.body.appendChild(tip);
  function show(event) {
    var cell = event.target.closest('a');
    if (!cell) { tip.hidden = true; return; }
    var box = cell.getBoundingClientRect();
    tip.innerHTML = '<b>' + esc(cell.dataset.name) + '</b><span>' + esc(cell.dataset.no) + '</span>';
    tip.style.left = (box.left + box.width / 2) + 'px';
    tip.style.top = box.top + 'px';
    tip.hidden = false;
  }
  matrix.addEventListener('pointerover', show);
  matrix.addEventListener('focusin', show);
  matrix.addEventListener('pointerleave', function () { tip.hidden = true; });
  matrix.addEventListener('focusout', function () { tip.hidden = true; });
}

async function initHome() {
  renderChrome('');
  renderMatrix();
  byId('bestseller-grid').innerHTML = skeletonCards(8);
  try {
    var data = await api('/api/home');
    byId('numbers').innerHTML =
      '<div class="cell"><div class="figure">' + count(data.orders) + '</div><div class="label">orders placed</div></div>'
      + '<div class="cell"><div class="figure">' + count(data.unitsSold) + '</div><div class="label">pieces sold</div></div>'
      + '<div class="cell"><div class="figure">' + shortRupees(data.revenue) + '</div><div class="label">revenue so far</div></div>'
      + '<div class="cell"><div class="figure">' + count(data.productCount) + '</div><div class="label">products, all in stock</div></div>';

    byId('categories').innerHTML = data.categories.map(function (c) {
      return '<a class="cat-cell" href="/products?category=' + esc(c.slug) + '">' + drawing(c.name)
        + '<div class="name">' + esc(c.name) + '</div>'
        + '<div class="meta mono"><span>' + c.productCount + ' finishes</span><span>from ' + rupees(c.fromPrice) + '</span></div></a>';
    }).join('');

    byId('bestseller-grid').innerHTML = data.bestsellers.map(card).join('');
  } catch (error) {
    showProblem(byId('bestseller-grid'), error);
  }
}

/* ---------- product list page ---------- */

async function initList() {
  var slug = new URLSearchParams(location.search).get('category') || 'all';
  renderChrome(slug);
  var grid = byId('grid');
  grid.innerHTML = skeletonCards(12);

  var products = [];
  function paint() {
    var words = byId('search').value.trim().toLowerCase();
    var order = byId('sort').value;
    var shown = products.filter(function (p) { return p.name.toLowerCase().indexOf(words) >= 0; });
    shown.sort(function (a, b) {
      if (order === 'low') { return a.price - b.price; }
      if (order === 'high') { return b.price - a.price; }
      if (order === 'name') { return a.name.localeCompare(b.name); }
      return a.rank - b.rank;
    });
    byId('count').textContent = shown.length + (shown.length === 1 ? ' product' : ' products');
    grid.innerHTML = shown.length ? shown.map(card).join('')
      : '<p class="empty">Nothing matches “' + esc(byId('search').value) + '”. Try a colour or an object, like “blue” or “lamp”.</p>';
  }

  try {
    var data = await api('/api/products?category=' + encodeURIComponent(slug));
    products = data.products;
    byId('list-title').textContent = data.categoryName;
    document.title = data.categoryName + ' · Cachet';
    byId('list-kicker').textContent = slug === 'all' ? 'The whole catalogue' : 'One object, twenty finishes';
    byId('search').addEventListener('input', paint);
    byId('sort').addEventListener('change', paint);
    paint();
  } catch (error) {
    showProblem(grid, error);
  }
}

/* ---------- product page ---------- */

async function initProduct() {
  var id = parseInt(new URLSearchParams(location.search).get('id'), 10) || 0;
  var box = byId('product');
  renderChrome('');
  try {
    var data = await api('/api/product?id=' + id);
    var p = data.product;
    var style = styleOf(p.style);
    document.title = p.name + ' · Cachet';
    // mark this product's category in the strip under the header
    document.querySelectorAll('.catnav a').forEach(function (a) {
      if (a.getAttribute('href') === '/products?category=' + p.slug) { a.setAttribute('aria-current', 'page'); }
    });

    var categoryIndex = CATEGORIES.indexOf(p.category);
    var finishes = STYLES.map(function (s, i) {
      var otherId = i * 10 + categoryIndex + 1;
      return '<a href="/product?id=' + otherId + '" style="--c:' + s[1] + '" title="' + esc(s[0]) + '"'
        + ' class="' + (isPale(s[1]) ? 'pale' : '') + '"' + (otherId === p.id ? ' aria-current="true"' : '')
        + ' aria-label="' + esc(s[0] + ' ' + p.category) + '"></a>';
    }).join('');

    var most = Math.max.apply(null, data.ordersByQuantity) || 1;
    var sizes = data.ordersByQuantity.map(function (n, i) {
      var label = (i + 1) + (i === 0 ? ' piece' : ' pieces');
      return '<div class="size-row"><span>' + label + '</span><div class="track"><div class="fill" style="width:'
        + (100 * n / most).toFixed(1) + '%"></div></div><span class="n">' + count(n) + '</span></div>';
    }).join('');

    byId('crumbs').innerHTML = '<a href="/">Home</a> / <a href="/products?category=' + esc(p.slug) + '">'
      + esc(p.category) + '</a> / ' + esc(p.style);

    box.innerHTML = chip(p)
      + '<div><span class="mono" style="color:var(--ink-3)">' + number3(p.id) + ' · ' + esc(p.category) + '</span>'
      + '<h1 class="display">' + esc(p.name) + '</h1>'
      + '<div class="price">' + rupees(p.price) + '</div>'
      + '<p class="standing">' + ordinal(p.rank) + ' of ' + data.productCount + ' products by revenue</p>'
      + '<p class="words">' + esc(WORDS[p.category] || '') + ' ' + esc(FINISH_NOTE[p.style] || 'Shown in ' + p.style.toLowerCase() + '.') + '</p>'
      + '<div class="finishes"><div class="label mono"><span>Finish: ' + esc(p.style) + '</span><span>20 available</span></div>'
      + '<div class="finish-row">' + finishes + '</div></div>'
      + '<table class="spec">'
      + '<tr><th>Orders</th><td>' + count(p.orders) + '</td></tr>'
      + '<tr><th>Pieces sold</th><td>' + count(p.unitsSold) + '</td></tr>'
      + '<tr><th>Revenue</th><td>' + rupees(p.revenue) + '</td></tr>'
      + '<tr><th>Average pieces per order</th><td>' + (p.unitsSold / p.orders).toFixed(2) + '</td></tr>'
      + '</table>'
      + '<div class="sizes"><h2>How many pieces people order</h2><p>Number of orders by size, counted from every order of this product.</p>'
      + sizes + '</div></div>';

    var plural = p.category.toLowerCase() + (p.category.slice(-1) === 's' ? '' : 's');
    byId('related-title').textContent = 'More ' + plural;
    byId('related-link').setAttribute('href', '/products?category=' + p.slug);
    byId('related').innerHTML = data.related.map(card).join('');
  } catch (error) {
    showProblem(box, error);
  }
}

var page = document.body.dataset.page;
if (page === 'home') { initHome(); }
if (page === 'list') { initList(); }
if (page === 'product') { initProduct(); }
