# cse306-project-3-Devices
Initially there is one queue of I/O requests.\

• When the device starts processing the first request in that queue, the queue is closed and
any subsequent I/O requests are added to another queue.\
• When the device is done with the first queue, it switches to the second. That second queue
is then closed and new requests go into a third queue, etc.\
• Within each queue, requests are processed using SSTF (shortest seek time first)
