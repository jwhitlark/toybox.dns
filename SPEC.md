## TODO

Implement A and SRV records, UDP only?

## Reference

https://en.wikipedia.org/wiki/Domain_Name_System

https://en.wikipedia.org/wiki/List_of_DNS_record_types

RFC ?

## Transport

DNS primarily uses User Datagram Protocol (UDP) on port number 53 to serve requests.[3] DNS queries consist of a single UDP request from the client followed by a single UDP reply from the server. The Transmission Control Protocol (TCP) is used when the response data size exceeds 512 bytes, or for tasks such as zone transfers. Some resolver implementations use TCP for all queries.
