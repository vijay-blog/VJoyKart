# VJoyKart customer-side delivery dispatch change

Customer order creation keeps the customer/admin order flow intact, but it no longer sends a broadcast notification to every online delivery partner.

The partner backend owns the 5 km -> 8 km radius dispatch workflow. It reads the customer delivery address coordinates from the shared NexaMart MySQL schema and sends assignment notifications only to eligible nearby delivery partners.
