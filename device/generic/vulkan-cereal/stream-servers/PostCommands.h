#pragma once

#include <GLES2/gl2.h>

#include <functional>
#include <future>
#include <memory>
#include <vector>

class ColorBuffer;

// Posting
enum class PostCmd {
    Post = 0,
    Viewport = 1,
    Compose = 2,
    Clear = 3,
    Screenshot = 4,
    Exit = 5,
};

struct Post {
    using ComposeCallback =
        std::function<void(std::shared_future<void> waitForGpu)>;
    PostCmd cmd;
    int composeVersion;
    std::vector<char> composeBuffer;
    std::shared_ptr<ComposeCallback> composeCallback = nullptr;
    union {
        ColorBuffer* cb;
        struct {
            int width;
            int height;
        } viewport;
        struct {
            ColorBuffer* cb;
            int screenwidth;
            int screenheight;
            GLenum format;
            GLenum type;
            int rotation;
            void* pixels;
        } screenshot;
    };
};
