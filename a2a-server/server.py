"""Sample A2A agent server — 供 gimi A2A 客户端联调（JSON-RPC over HTTP）。

基于官方 a2a-sdk 1.x（protobuf 类型层）的 Sample Agent 示例，针对 gimi 的
A2A 客户端（adk-kotlin + a2a-java，只消费 JSON-RPC）适配：

- 默认绑定并广播 127.0.0.1；局域网需显式 --host 0.0.0.0 和 --advertise-host
  指定真实 IP（Clash TUN 会劫持自动探测）；
- gRPC / v0.3 兼容层可选（--enable-grpc / --enable-v0-3-compat），基础形态
  只依赖 a2a-sdk[http-server]；
- supportedInterfaces 按实际启用的传输动态生成。

`create_app` / `build_agent_card` / `SampleAgentExecutor` 供 tests/ 复用，
行为契约见 tests/test_server.py。
"""

import argparse
import asyncio
import contextlib
import logging
import socket

from a2a.utils.errors import InvalidParamsError

import uvicorn
from fastapi import FastAPI

from a2a.server.agent_execution.agent_executor import AgentExecutor
from a2a.server.agent_execution.context import RequestContext
from a2a.server.events.event_queue import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import (
    add_a2a_routes_to_fastapi,
    create_agent_card_routes,
    create_jsonrpc_routes,
    create_rest_routes,
)
from a2a.server.tasks.inmemory_task_store import InMemoryTaskStore
from a2a.server.tasks.task_updater import TaskUpdater
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentInterface,
    AgentProvider,
    AgentSkill,
    Part,
    Task,
    TaskState,
    TaskStatus,
)


logger = logging.getLogger(__name__)

JSONRPC_URL = '/a2a/jsonrpc'
REST_PREFIX = '/a2a/rest'


def detect_lan_ip() -> str:
    """探测本机局域网出口 IP（不真正发包）；失败回退 127.0.0.1。"""
    with contextlib.closing(socket.socket(socket.AF_INET, socket.SOCK_DGRAM)) as s:
        try:
            s.connect(('8.8.8.8', 80))
            return s.getsockname()[0]
        except OSError:
            return '127.0.0.1'


class SampleAgentExecutor(AgentExecutor):
    """Keyword-triggered sample executor, mirroring the a2a-js sample."""

    def __init__(self) -> None:
        self.running_tasks: set[str] = set()

    async def cancel(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        task_id = context.task_id
        if not task_id or not context.context_id:
            raise InvalidParamsError('task_id and context_id are required')
        self.running_tasks.discard(task_id)

        updater = TaskUpdater(
            event_queue=event_queue,
            task_id=task_id,
            context_id=context.context_id,
        )
        await updater.cancel()

    async def execute(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        user_message = context.message
        task_id = context.task_id
        context_id = context.context_id

        if not user_message or not task_id or not context_id:
            raise InvalidParamsError('message, task_id and context_id are required')

        self.running_tasks.add(task_id)

        try:
            logger.info(
                '[SampleAgentExecutor] Processing message %s for task %s (context: %s)',
                user_message.message_id,
                task_id,
                context_id,
            )

            await event_queue.enqueue_event(
                Task(
                    id=task_id,
                    context_id=context_id,
                    status=TaskStatus(state=TaskState.TASK_STATE_SUBMITTED),
                    history=[user_message],
                )
            )

            updater = TaskUpdater(
                event_queue=event_queue,
                task_id=task_id,
                context_id=context_id,
            )

            working_message = updater.new_agent_message(
                parts=[Part(text='Processing your question...')]
            )
            await updater.start_work(message=working_message)

            agent_reply_text = self._parse_input(context.get_user_input())
            await asyncio.sleep(1)

            if task_id not in self.running_tasks:
                return

            await updater.add_artifact(
                parts=[Part(text=agent_reply_text)],
                name='response',
                last_chunk=True,
            )
            await updater.complete()

            logger.info('[SampleAgentExecutor] Task %s finished with state: completed', task_id)
        finally:
            self.running_tasks.discard(task_id)

    def _parse_input(self, query: str) -> str:
        if not query:
            return 'Hello! Please provide a message for me to respond to.'

        ql = query.lower()
        if 'hello' in ql or 'hi' in ql:
            return 'Hello World! Nice to meet you!'
        if 'how are you' in ql:
            return "I'm doing great! Thanks for asking. How can I help you today?"
        if 'goodbye' in ql or 'bye' in ql:
            return 'Goodbye! Have a wonderful day!'
        return f"Hello World! You said: '{query}'. Thanks for your message!"


def build_agent_card(
    advertise_host: str,
    port: int,
    *,
    grpc_enabled: bool = False,
    grpc_port: int = 50051,
    v03_compat: bool = False,
) -> AgentCard:
    """按实际启用的传输生成 1.0 卡片；JSONRPC interface 是 gimi 客户端消费的目标。"""
    interfaces = [
        AgentInterface(
            protocol_binding='JSONRPC',
            protocol_version='1.0',
            url=f'http://{advertise_host}:{port}{JSONRPC_URL}',
        ),
        AgentInterface(
            protocol_binding='HTTP+JSON',
            protocol_version='1.0',
            url=f'http://{advertise_host}:{port}{REST_PREFIX}',
        ),
    ]
    if v03_compat:
        interfaces += [
            AgentInterface(
                protocol_binding='JSONRPC',
                protocol_version='0.3',
                url=f'http://{advertise_host}:{port}{JSONRPC_URL}',
            ),
            AgentInterface(
                protocol_binding='HTTP+JSON',
                protocol_version='0.3',
                url=f'http://{advertise_host}:{port}{REST_PREFIX}',
            ),
        ]
    if grpc_enabled:
        interfaces.append(
            AgentInterface(
                protocol_binding='GRPC',
                protocol_version='1.0',
                url=f'{advertise_host}:{grpc_port}',
            ),
        )

    return AgentCard(
        name='Sample Agent',
        description='A sample agent to test the gimi A2A client.',
        provider=AgentProvider(organization='A2A Samples', url='https://example.com'),
        version='1.0.0',
        capabilities=AgentCapabilities(streaming=True, push_notifications=False),
        default_input_modes=['text'],
        default_output_modes=['text', 'task-status'],
        skills=[
            AgentSkill(
                id='sample_agent',
                name='Sample Agent',
                description='Say hi.',
                tags=['sample'],
                examples=['hi'],
                input_modes=['text'],
                output_modes=['text', 'task-status'],
            )
        ],
        supported_interfaces=interfaces,
    )


def create_app(
    advertise_host: str = '127.0.0.1',
    port: int = 41241,
    *,
    v03_compat: bool = False,
    grpc_enabled: bool = False,
    grpc_port: int = 50051,
) -> FastAPI:
    """组装 FastAPI 应用（卡片 + JSON-RPC + REST 路由）；测试经 ASGI 复用。"""
    agent_card = build_agent_card(advertise_host, port, v03_compat=v03_compat,
                                  grpc_enabled=grpc_enabled, grpc_port=grpc_port)
    request_handler = DefaultRequestHandler(
        agent_executor=SampleAgentExecutor(),
        task_store=InMemoryTaskStore(),
        agent_card=agent_card,
    )
    app = FastAPI()
    app.state.request_handler = request_handler
    add_a2a_routes_to_fastapi(
        app,
        agent_card_routes=create_agent_card_routes(agent_card=agent_card),
        jsonrpc_routes=create_jsonrpc_routes(
            request_handler=request_handler,
            rpc_url=JSONRPC_URL,
            enable_v0_3_compat=v03_compat,
        ),
        rest_routes=create_rest_routes(
            request_handler=request_handler,
            path_prefix=REST_PREFIX,
            enable_v0_3_compat=v03_compat,
        ),
    )
    return app


async def serve(
    host: str = '127.0.0.1',
    advertise_host: str | None = None,
    port: int = 41241,
    enable_grpc: bool = False,
    grpc_port: int = 50051,
    enable_v03_compat: bool = False,
) -> None:
    """Run the Sample Agent server with the enabled transports mounted."""
    advertised = advertise_host or (host if host not in ('0.0.0.0', '::') else detect_lan_ip())
    app = create_app(advertised, port, v03_compat=enable_v03_compat,
                     grpc_enabled=enable_grpc, grpc_port=grpc_port)
    grpc_server = None
    http_server = uvicorn.Server(uvicorn.Config(app, host=host, port=port))
    servers: list[asyncio.Task] = []
    try:
        if enable_grpc:
            import grpc
            from a2a.server.request_handlers import GrpcHandler
            from a2a.types import a2a_pb2_grpc

            grpc_server = grpc.aio.server()
            if not grpc_server.add_insecure_port(f'{host}:{grpc_port}'):
                raise RuntimeError(f'Cannot bind gRPC port {grpc_port}')
            # HTTP 与 gRPC 共用任务存储和执行器，跨传输查询/取消同一任务。
            a2a_pb2_grpc.add_A2AServiceServicer_to_server(
                GrpcHandler(app.state.request_handler), grpc_server,
            )
            await grpc_server.start()
            servers.append(asyncio.create_task(grpc_server.wait_for_termination()))
        servers.append(asyncio.create_task(http_server.serve()))
        logger.info('Starting Sample Agent (bind %s, advertise %s:%s)', host, advertised, port)
        done, _ = await asyncio.wait(servers, return_when=asyncio.FIRST_COMPLETED)
        for task in done:
            task.result()
    finally:
        http_server.should_exit = True
        if grpc_server is not None:
            await grpc_server.stop(5)
        for task in servers:
            if not task.done():
                task.cancel()
        await asyncio.gather(*servers, return_exceptions=True)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    parser = argparse.ArgumentParser(description='Sample A2A agent server')
    parser.add_argument('--host', default='127.0.0.1', help='绑定地址；0.0.0.0 允许局域网访问')
    parser.add_argument(
        '--advertise-host',
        default=None,
        help='写入 Agent Card 的对外地址（默认使用绑定地址，绑定全部接口时自动探测局域网 IP）；手机需与该地址同网可达',
    )
    parser.add_argument('--port', type=int, default=41241)
    parser.add_argument('--enable-grpc', action='store_true', help='同时启动 gRPC 1.0 传输（需 grpc 依赖）')
    parser.add_argument('--grpc-port', type=int, default=50051)
    parser.add_argument('--enable-v0-3-compat', action='store_true', help='启用 v0.3 兼容路由与卡片 interface')
    args = parser.parse_args()
    with contextlib.suppress(KeyboardInterrupt):
        asyncio.run(
            serve(
                host=args.host,
                advertise_host=args.advertise_host,
                port=args.port,
                enable_grpc=args.enable_grpc,
                grpc_port=args.grpc_port,
                enable_v03_compat=args.enable_v0_3_compat,
            )
        )
