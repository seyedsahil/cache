

### Test 1 (Lock in Bucket.Put and Bucket.Remove - No Background Job for Invalidation)

```
Thread Count: 100
Cache Size: 10000000
Key Size: 16
Total of Cache.put(key): 6.438771628E11 nanoseconds / 6438.771628 milliseconds
Average of Cache.put(key): 64387.71628 nanoseconds / 0.06438771628 milliseconds
Total of Cache.get(key): 5.2594537E9 nanoseconds / 52.594537 milliseconds
Average of Cache.get(key): 525.94537 nanoseconds / 5.2594537E-4 milliseconds
Total of Cache.remove(key): 2.559725141E11 nanoseconds / 2559.725141 milliseconds
Average of Cache.remove(key): 25597.25141 nanoseconds / 0.02559725141 milliseconds
```