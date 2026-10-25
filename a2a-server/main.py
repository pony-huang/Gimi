"""uv 入口：与 `python server.py` 等价，供 `uv run main.py` 使用。"""

import asyncio
import contextlib
import logging

from server import serve

if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    with contextlib.suppress(KeyboardInterrupt):
        asyncio.run(serve())
