# 放射性衰变链核算服务（decay-chain-service）

纯服务端的线性核素链动力学核算组件。上游把一条 `N1 → N2 → … → Nk` 单向衰变链的
**衰变常数**与**初始核数**交给服务，服务在给定时刻返回各核素的**核数**与**活度**，
并报告母体剩余、链上原子总数（末核稳定时另报已迁入稳定终点的核数）。

- Java 17 + Spring Boot 3.3（Web / JPA / Actuator / Prometheus）
- 解析求解：带重根极限处理的广义 Bateman 公式（不做数值积分，无步长误差）
- 持久化：PostgreSQL（Hibernate 自动建表），每次请求与结果都留痕，可按条件查历史
- Docker Compose 一键启动服务与数据库

## 快速开始

```bash
docker compose up --build
# 服务：http://localhost:8080
```

本机直接运行（需要 JDK17+、Maven 3.9+ 与一个 Postgres；可用环境变量覆盖连接）：

```bash
mvn spring-boot:run
# 或
mvn package && java -jar target/decay-chain-service-1.0.0.jar
```

环境变量：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`，以及容差/上限配置
（见 `src/main/resources/application.yml`，松散绑定，如 `DECAY_MAX-GRID-STEPS`）。

## 预置算例

`GET /api/decay/example` 无需请求体，返回 3 核算例（母体 λ=0.01、短寿命子体 λ=10、
末核稳定，初始 10⁶ 个母体原子）在 0/0.01/0.05/0.1/0.5/1.0 的结果。
极短时间内子体生长量与 `λ1·N1(0)·t` 同量级。

## HTTP 接口

### 1. 显式时刻求解 `POST /api/decay/solve`

```json
{
  "decayConstants": [0.1, 0.5, 0.0],
  "initialNumbers": [1000, 0, 0],
  "nuclides": ["A", "B", "C"],
  "times": [0, 1, 5, 100]
}
```

- `decayConstants`：长度即链长，允许 2–12。仅末位允许 `0`（稳定终点）。
- `initialNumbers`：长度必须与衰变常数一致，非负。
- `times`：非负有限时刻，可任意序。

响应每个时刻给出：

```json
{
  "time": 5.0,
  "numbers": [606.53, 131.11, 262.36],
  "activities": [60.653, 65.556, 0.0],
  "parentRemaining": 606.53,
  "parentRemainingFraction": 0.6065,
  "chainRemainingAtoms": 1000.0,
  "activeAtoms": 737.64,
  "stableEndpointAtoms": 262.36,
  "initialTotalAtoms": 1000.0,
  "conservationResidual": 0.0
}
```

末核不稳定时 `activeAtoms` / `stableEndpointAtoms` / `conservationResidual` 为 `null`
（全不稳定链是开放系统，原子随末核衰变离开）。

### 2. 均匀网格 `POST /api/decay/grid`

```json
{ "decayConstants": [0.1, 0.0], "initialNumbers": [100, 0],
  "startTime": 0, "endTime": 10, "steps": 100 }
```

生成含两端共 `steps+1` 个等距时刻。`steps` 超过上限返回结构化错误。

### 3. 批量核算 `POST /api/decay/batch`

```json
{ "items": [ { …单组 solve/grid 请求… }, { … } ] }
```

每组可用 `times` 或 `startTime/endTime/steps`。某组非法不影响其它组，HTTP 仍为 200：

```json
{ "total": 2, "succeeded": 1, "failed": 2,
  "items": [
    { "index": 1, "ok": true,  "result": { …SolveResponse… }, "issues": [] },
    { "index": 2, "ok": false, "result": null,
      "issues": [ { "code": "NEGATIVE_LAMBDA", "message": "…",
                    "parameter": "decayConstants",
                    "nuclideIndex": 2, "batchIndex": 2 } ] } ] }
```

### 4. 历史查询

- `GET /api/history?mode=SOLVE&success=true&chainLength=3&from=<ISO>&to=<ISO>&page=0&size=20`
- `GET /api/history/{id}`：单条详情，含原始请求与结果 JSON

成功与失败请求都会落库；失败记录 `success=false` 且带错误摘要。

### 5. 配置回显 / 状态 / 监控

- `GET /api/config`：链长上限、近等常数容差、步数/时刻/批量上限、守恒容差
- `GET /api/status`：基本运行状态 + 已持久化记录数
- `GET /actuator/health`（含 liveness/readiness 子探针）
- `GET /actuator/prometheus`：JVM 与 HTTP 指标，Prometheus 直接抓取

## 物理模型与数值方法

- ODE：`dN1/dt = -λ1 N1`，`dNi/dt = λ_{i-1} N_{i-1} - λ_i Ni`，末核 λ 可为 0。
- 活度：`Ai = λi Ni`；稳定核活度恒为 0。
- 求解：广义 Bateman 解析解。对每个源核 s→目标核 i，
  传递函数为 `C / ∏_{j=s}^{i}(z+λ_j)` 的拉普拉斯逆变换。
- **近等/相等衰变常数**：常数先按相对容差聚类，同簇按重根极限展开
  （`t^q e^{-at}` 项），分母为簇间差，天然不除零；全部指数项按
  带符号 log-sum-exp 在对数空间累加，不溢出。
- 零时刻走精确分支，逐位返回初值。
- 末核稳定时做原子守恒校验（`activeAtoms + stableEndpointAtoms = initialTotal`，
  默认相对容差 1e-6），超差返回 422 结构化错误，绝不返回“看似正常”的数。

## 结构化错误

任何输入问题都是 `400` + 可读 JSON（守恒失败为 `422`，记录不存在为 `404`）：

```json
{ "error": "VALIDATION_FAILED", "message": "输入校验未通过", "requestId": null,
  "issues": [ { "code": "NEGATIVE_LAMBDA", "message": "衰变常数不能为负：-0.5",
                "parameter": "decayConstants",
                "nuclideIndex": 2, "batchIndex": null } ] }
```

覆盖：缺字段、类型错误（布尔/字符串/对象冒充数值）、NaN/Infinity、`"NaN"` 字符串、
负衰变常数、负初值、把稳定核写成 `-0`、链长 <2 或 >12、长度不一致、
中间核衰变常数为 0、负/非有限时刻、步数超上限、批量信封缺失等。

## 代码结构

| 包 | 职责 |
|---|---|
| `model` | 核素链模型 |
| `solver` | 常数聚类 + 重根 Bateman 解析求解 |
| `physics` | 活度、母体剩余、原子总数与守恒校验 |
| `validation` | 基于 JSON 树的精确输入校验与结构化错误 |
| `persistence` | JPA 实体与仓库 |
| `service` | 校验→核算→响应组装→留痕 的编排（无共享可变状态） |
| `web` | REST 控制器、DTO、全局异常处理 |
| `config` | 属性绑定与 Bean 装配 |

## 测试

```bash
mvn test
```

58 个自动化测试（H2 内存库），覆盖需求列出的全部规则：

- 零时刻逐位回到初值
- 全不稳定链总核数严格下降；开端下降率 = `λ1·N1(0)`
- 末核稳定时长期稳定核 → 初始总数、其余 → 0，原子全程守恒
- 全不稳定时长期全部 → 0
- 子体 λ 远大于母体时核数逼近 `λ1 N1 / λ2`（母子活度平衡）
- 母体核数只按自身指数下降，与子体链设置无关
- 初值加倍 ⇒ 各核核数与活度全部加倍
- 相邻常数相等/差 5e-9 时结果仍有限、不爆炸，且与独立 RK4 积分一致
- 12 核含两组近等常数的链保持有限且守恒
- 各类非法参数被拒并指明错误码/第几组/第几个核/哪个参数
- 批量部分失败、其余成功
- 成功与失败请求均正确持久化、条件过滤与详情回放
- 24 线程 × 20 组并发请求结果互不串扰
