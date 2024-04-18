

### Test 1 (Lock in Bucket.Put and Bucket.Remove - With Background Job for Invalidation)

```
Thread Count: 100
Cache Size: 10000000
Key Size: 16
Cached Records Count after Put Finishes (Invalidation Executed In Between): 5562883
Total of Cache.put(key): 5.572346348E11 nanoseconds / 5572.346348 milliseconds
Average of Cache.put(key): 55723.46348 nanoseconds / 0.05572346348 milliseconds
Total of Cache.get(key): 4.81462039E10 nanoseconds / 481.462039 milliseconds
Average of Cache.get(key): 4814.62039 nanoseconds / 0.00481462039 milliseconds
Total of Cache.remove(key): 2.451887481E11 nanoseconds / 2451.8874809999998 milliseconds
Average of Cache.remove(key): 24518.87481 nanoseconds / 0.02451887481 milliseconds
```