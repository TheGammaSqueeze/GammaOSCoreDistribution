# RKNN C API Dynamic Shape Input Demo

This is a demo that uses the RKNN C API for dynamic shape input inference. In this demo, you can see how to use the RKNN dynamic shape C API to perform image classification.

## How to Use

1. Clone or download this code repository: ssh://git@10.10.10.59:8001/hpc/rknpu2.git.
2. Navigate to the dynamic shape inference demo directory in your terminal.

```shell
cd examples/rknn_dynamic_shape_input_demo
```

3. Compile the application by running the shell script based on the chip platform. For example, for the RK3562 Android system, run the following command:

```shell
./build-android_RK3562.sh
```

4. Push the demo program directory to the target board's system using the adb command. For example:

```shell
#If using Android system, make sure to run adb root & adb remount first.
adb push ./install/rknn_dynshape_demo_Android/ /data
```

5. Set the runtime library path.

```
export LD_LIBRARY_PATH=./lib
```

6. Run the program. For example, on the RK3562 platform, use the command

   ```shell
   ./rknn_dynshape_inference model/RK3562/mobilenet_v2.rknn images/dog_224x224.jpg
   ```

   ,where `mobilenet_v2.rknn` is the name of the neural network model file, and `dog_224x224.jpg` is the name of the image file to classify.

## Compilation Instructions

### Arm Linux

Specify the cross-compiler path for the specific chip platform by modifying the `GCC_COMPILER` in `build-linux_<TARGET_PLATFORM>.sh`, where TARGET_PLATFORM is the chip name. Then execute:

```
./build-linux_<TARGET_PLATFORM>.sh
```

### Android

Specify the path to the Android NDK by modifying `ANDROID_NDK_PATH` in `build-android_<TARGET_PLATFORM>.sh`, where TARGET_PLATFORM is the chip name. Then execute:

```
./build-android_<TARGET_PLATFORM>.sh
```

## Included Features

This demonstration application includes the following features:

- Creating a neural network model with dynamic shape inputs. Please refer to the examples/functions/dynamic_input directory in the https://github.com/rockchip-linux/rknn-toolkit2 repository for more information.
- Reading an image from a file and performing classification using the neural network model. The program follows these steps:

1. Initialize the RKNN context using the `rknn_init()` function.
2. Set the shape information of all the model inputs using the `rknn_set_input_shapes()` function, including shape and layout.
3. Query the current model input and output information, including shape, data type, and size, using the `rknn_query()` function.
4. Set the input data of the model using the `rknn_inputs_set()` function, including data pointer and size.
5. Run the model using the `rknn_run()` function.
6. Retrieve the output data by using the `rknn_outputs_get()` function, specifying the need for float-type results.
7. Process the output data to obtain the classification results and probabilities.
8. Release the RKNN context using the `rknn_release()` function.