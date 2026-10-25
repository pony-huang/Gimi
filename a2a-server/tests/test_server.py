"""Sample A2A server 的行为契约测试（进程内 ASGI，无真实网络）。

固化 gimi 联调期间验证过的线协议行为，尤其 1.x 的两个坑：
- JSON-RPC 方法名为 PascalCase（SendMessage/SendStreamingMessage），
  0.3 风格的 message/send 返回 Method not found；
- 所有请求必须携带 A2A-Version: 1.0 头，缺失报 VERSION_NOT_SUPPORTED。
"""

import json

import pytest
from fastapi.testclient import TestClient

from server import JSONRPC_URL, build_agent_card, create_app

JSONRPC_METHOD_NOT_FOUND = -32601
A2A_VERSION_NOT_SUPPORTED = -32009

HEADERS_V1 = {'A2A-Version': '1.0'}


def rpc(method: str, params: dict, request_id: int = 1) -> dict:
    return {'jsonrpc': '2.0', 'id': request_id, 'method': method, 'params': params}


def user_message(text: str, message_id: str = 'msg-1') -> dict:
    return {
        'message': {
            'role': 'ROLE_USER',
            'parts': [{'text': text}],
            'messageId': message_id,
        }
    }


@pytest.fixture
def client() -> TestClient:
    return TestClient(create_app(advertise_host='127.0.0.1', port=41241))


def test_card_exposes_jsonrpc_1_0_interface(client: TestClient):
    """gimi 客户端只消费 1.0 卡片里的 JSONRPC interface。"""
    response = client.get('/.well-known/agent-card.json')
    assert response.status_code == 200

    card = response.json()
    assert card['version'] == '1.0.0'
    assert card['capabilities']['streaming'] is True

    jsonrpc_interfaces = [
        interface
        for interface in card['supportedInterfaces']
        if interface['protocolBinding'] == 'JSONRPC'
    ]
    assert len(jsonrpc_interfaces) == 1
    interface = jsonrpc_interfaces[0]
    assert interface['protocolVersion'] == '1.0'
    assert interface['url'].endswith(JSONRPC_URL)
    # 卡片广播的是调用方传入的 advertise_host，而非本机探测值。
    assert '127.0.0.1:41241' in interface['url']


def test_card_builder_appends_optional_interfaces():
    """gRPC 与 0.3 兼容 interface 仅在启用时广播。"""
    card = build_agent_card('10.1.2.197', 41241, grpc_enabled=True, v03_compat=True)
    bindings = {(i.protocol_binding, i.protocol_version) for i in card.supported_interfaces}
    assert ('JSONRPC', '1.0') in bindings
    assert ('JSONRPC', '0.3') in bindings
    assert ('HTTP+JSON', '1.0') in bindings
    assert ('HTTP+JSON', '0.3') in bindings
    assert ('GRPC', '1.0') in bindings

    minimal = build_agent_card('10.1.2.197', 41241)
    assert {(i.protocol_binding, i.protocol_version) for i in minimal.supported_interfaces} == {
        ('JSONRPC', '1.0'),
        ('HTTP+JSON', '1.0'),
    }


def test_send_message_completes_with_reply(client: TestClient):
    """非流式 SendMessage：task 走到 completed 且 artifact 携带回复。"""
    response = client.post(
        JSONRPC_URL, json=rpc('SendMessage', user_message('hi')), headers=HEADERS_V1
    )
    assert response.status_code == 200

    result = response.json()['result']
    assert result['task']['status']['state'] == 'TASK_STATE_COMPLETED'

    reply_chunks = [
        part['text']
        for artifact in result['task']['artifacts']
        for part in artifact['parts']
        if 'text' in part
    ]
    assert ''.join(reply_chunks) == 'Hello World! Nice to meet you!'


def test_send_message_requires_version_header(client: TestClient):
    """缺失 A2A-Version 头按 0.3 处理，被 1.0 handler 拒绝。"""
    response = client.post(
        JSONRPC_URL, json=rpc('SendMessage', user_message('hi')), headers={}
    )
    assert response.status_code == 200

    body = response.json()
    assert body['error']['code'] == A2A_VERSION_NOT_SUPPORTED
    assert "Expected version '1.0'" in body['error']['message']


def test_legacy_snake_case_method_is_not_found(client: TestClient):
    """0.3 的 message/send 在 1.x 已更名为 SendMessage（PascalCase）。"""
    response = client.post(
        JSONRPC_URL,
        json=rpc('message/send', user_message('hi')),
        headers=HEADERS_V1,
    )
    assert response.status_code == 200

    body = response.json()
    assert body['error']['code'] == JSONRPC_METHOD_NOT_FOUND


def test_streaming_emits_full_lifecycle(client: TestClient):
    """流式 SendStreamingMessage：SSE 事件覆盖 submitted→working→artifact→completed。"""
    events: list[dict] = []

    with client.stream(
        'POST',
        JSONRPC_URL,
        json=rpc('SendStreamingMessage', user_message('how are you'), request_id=2),
        headers=HEADERS_V1,
    ) as response:
        assert response.status_code == 200
        assert response.headers['content-type'].startswith('text/event-stream')

        for line in response.iter_lines():
            if line.startswith('data: '):
                events.append(json.loads(line.removeprefix('data: ')))

    assert events, 'expected at least one SSE event'

    states: list[str] = []
    artifact_texts: list[str] = []
    for event in events:
        result = event['result']
        if 'task' in result:
            states.append(result['task']['status']['state'])
        if 'statusUpdate' in result:
            states.append(result['statusUpdate']['status']['state'])
        if 'artifactUpdate' in result:
            artifact_texts += [
                part['text']
                for part in result['artifactUpdate']['artifact']['parts']
                if 'text' in part
            ]

    assert states[0] == 'TASK_STATE_SUBMITTED'
    assert 'TASK_STATE_WORKING' in states
    assert states[-1] == 'TASK_STATE_COMPLETED'
    assert ''.join(artifact_texts) == "I'm doing great! Thanks for asking. How can I help you today?"


def test_streaming_last_chunk_marks_completion(client: TestClient):
    """最终 artifactUpdate 携带 lastChunk，客户端聚合器据此收尾。"""
    last_artifact_update = None

    with client.stream(
        'POST',
        JSONRPC_URL,
        json=rpc('SendStreamingMessage', user_message('bye'), request_id=3),
        headers=HEADERS_V1,
    ) as response:
        for line in response.iter_lines():
            if not line.startswith('data: '):
                continue
            event = json.loads(line.removeprefix('data: '))
            result = event['result']
            if 'artifactUpdate' in result:
                last_artifact_update = result['artifactUpdate']

    assert last_artifact_update is not None
    assert last_artifact_update['lastChunk'] is True
