# Microdroid Payload

Payload disk is a composite disk image referencing host APEXes and an APK so that microdroid
mounts/activates APK/APEXes and executes a binary within the APK.

Payload disk is created by [VirtualizationService](../../virtualizationservice) Service when
starting a VM.

## Partitions

Payload disk has 1 + N(number of APEX/APK payloads) partitions.

The first partition is a "payload-metadata" partition which describes other partitions.
And APEXes and an APK are following as separate partitions.

For now, the order of partitions are important.

* partition 1: Metadata partition
* partition 2 ~ n: APEX payloads
* partition n+1, n+2: APK payload and its idsig

It's subject to change in the future, though.

### Metadata partition

Metadata partition provides description of the other partitions and the location for VM payload
configuration.

The partition is a protobuf message prefixed with the size of the message.

| offset | size | description                                          |
| ------ | ---- | ---------------------------------------------------- |
| 0      | 4    | Header. unsigned int32: body length(L) in big endian |
| 4      | L    | Body. A protobuf message. [schema](metadata.proto)   |

### Payload partitions

Each payload partition presents APEX or APK passed from the host.

The size of a payload partition must be a multiple of 4096 bytes.

# `mk_payload`

`mk_payload` is a small utility to create a payload disk image. It is used by ARCVM.

```
$ cat payload_config.json
{
  "apexes": [
    {
      "name": "com.my.hello",
      "path": "hello.apex",
    }
  ],
  "apk": {
    "name": "com.my.world",
    "path": "/path/to/world.apk",
    "idsigPath": "/path/to/world.apk.idsig",
  }
}
$ m mk_payload
$ mk_payload payload_config.json payload.img
$ ls
payload.img
payload-footer.img
payload-header.img
payload-metadata.img
payload-filler-0.img
payload-filler-1.img
...
```
