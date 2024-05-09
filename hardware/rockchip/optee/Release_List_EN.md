# Release Note

## optee v2

| Date       | file                                   | Build commit | Severity  |
| ---------- | :------------------------------------- | ------------ | --------- |
| 2023-05-18 | libckteec.so libteec.so tee-supplicant | 25920641     | important |

### New

1. When using security partitions for secure storage, data is synchronized every time it is written，it can increase secure storage stability, but read and write speeds may slow down.
2. Compile code using version 10.2 of the gcc compiler.
3. Accelerate rpmb reading and writing speed.

------

## optee v2

| Date       | file                 | Build commit | Severity |
| ---------- | :------------------- | ------------ | -------- |
| 2023-04-07 | librk_tee_service.so | 0435e966     | moderate |

### New

1. Support read secure boot enable flag.
2. Support read secure boot public key hash.

------

