# mini-redis-java

A small Redis-like key-value server written in Java, and a demo shop that uses it as its cache.

Full project report: [report/Redis_Mini_Project_Report.pdf](report/Redis_Mini_Project_Report.pdf)

## System diagrams

### System architecture

![System architecture](report/figures/system-architecture.png)

### Use case diagram

![Use case diagram](report/figures/use-case-diagram.png)

### Class diagram

![Class diagram](report/figures/class-diagram.png)

### Activity diagram: handling the command lines of one client

![Activity diagram](report/figures/activity-diagram.png)

### Saving a change while the server runs, and restoring the data when it starts

![Saving and restoring data](report/figures/save-and-restore.png)

### The shop without a cache, and with the server as its cache

![The shop with and without a cache](report/figures/shop-with-and-without-cache.png)

### Activity diagram: one page request of the shop

![One page request of the shop](report/figures/shop-one-request.png)

## Screenshots

### The server starting, and a client connected to it

![Server and client](report/figures/terminal-server-and-client.png)

### Two clients sharing values, a key with a time limit, and a list

![Two clients](report/figures/terminal-two-clients.png)

### The saved file, and the data read back after a restart

![Saved file and restart](report/figures/terminal-restart.png)

### Error replies for wrong commands, and a client started without a server

![Error replies](report/figures/terminal-errors.png)

### Output of the benchmark: 200 requests without the cache, then with it

![Benchmark output](report/figures/terminal-benchmark.png)

## License

[MIT](LICENSE)
