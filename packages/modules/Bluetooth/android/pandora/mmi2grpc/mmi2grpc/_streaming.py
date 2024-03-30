import queue


class IterableQueue:
    CLOSE = object()

    def __init__(self):
        self.queue = queue.Queue()

    def __iter__(self):
        return iter(self.queue.get, self.CLOSE)

    def put(self, value):
        self.queue.put(value)

    def close(self):
        self.put(self.CLOSE)


class StreamWrapper:

    def __init__(self, stream, ctor):
        self.tx_queue = IterableQueue()
        self.ctor = ctor

        # tx_queue is consumed on a separate thread, so
        # we don't block here
        self.rx_iter = stream(iter(self.tx_queue))

    def send(self, **kwargs):
        self.tx_queue.put(self.ctor(**kwargs))

    def __iter__(self):
        for value in self.rx_iter:
            yield value
        self.tx_queue.close()

    def recv(self):
        try:
            return next(self.rx_iter)
        except StopIteration:
            self.tx_queue.close()
            return

    def close(self):
        self.tx_queue.close()
