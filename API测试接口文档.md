# API 测试接口文档

本文档包含所有测试接口的详细说明，包括请求格式和响应示例。

> ⚠️ **注意：** 这些接口仅用于开发测试，生产环境应移除或限制访问。

---

## 目录

1. [StatTestController - 访问统计测试](#1-stattestcontroller---访问统计测试)
2. [HotspotTestController - 热点识别测试](#2-hotspottestcontroller---热点识别测试)
3. [StrategyTestController - 策略决策引擎测试](#3-strategytestcontroller---策略决策引擎测试)
4. [CacheProxyTestController - 缓存访问代理测试](#4-cacheproxytestcontroller---缓存访问代理测试)
5. [EndToEndTestController - 端到端测试](#5-endtoendtestcontroller---端到端测试)
6. [RateLimitTestController - 限流测试](#6-ratelimittestcontroller---限流测试)

---

## 1. StatTestController - 访问统计测试

**基础路径：** `/test/stat`

### 1.1 记录访问

**接口地址：** `GET /test/stat/record`

**描述：** 记录一次访问并返回统计结果

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |

**请求示例：**

```bash
GET http://localhost:8080/test/stat/record?bizType=product&bizKey=12345
```

**请求说明：**
- `bizType=product`：指定业务类型为商品（product）
- `bizKey=12345`：指定具体的业务键值为 12345
- 该请求会在 Redis 中记录一次访问，并返回当前统计结果

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "12345",
  "count1s": 1,
  "count60s": 1,
  "redisKey1s": "stat:product:12345:1s",
  "redisKey60s": "stat:product:12345:60s"
}
```

**响应说明：**
- `count1s`：最近 1 秒内的访问次数
- `count60s`：最近 60 秒内的访问次数
- `redisKey1s`：1 秒统计窗口在 Redis 中的键名
- `redisKey60s`：60 秒统计窗口在 Redis 中的键名

---

### 1.2 批量记录访问

**接口地址：** `GET /test/stat/batch`

**描述：** 批量记录访问（模拟并发场景）

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |
| count | Integer | 否 | 10 | 记录次数 |

**请求示例：**

```bash
GET http://localhost:8080/test/stat/batch?bizType=product&bizKey=12345&count=100
```

**请求说明：**
- `count=100`：连续记录 100 次访问
- 用于模拟高并发场景，测试统计服务的性能
- 可用于观察访问计数器的累加效果

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "12345",
  "recordCount": 100,
  "count1s": 100,
  "count60s": 100,
  "durationMs": 450,
  "avgLatencyMs": 4.5
}
```

**响应说明：**
- `recordCount`：实际记录的访问次数（应等于请求参数 count）
- `count1s` / `count60s`：最终的统计计数结果
- `durationMs`：批量记录耗时（毫秒）
- `avgLatencyMs`：平均每次记录的延迟（总耗时 ÷ 记录次数）

---

## 2. HotspotTestController - 热点识别测试

**基础路径：** `/test/hotspot`

### 2.1 热点检测

**接口地址：** `GET /test/hotspot/detect`

**描述：** 访问统计 + 热点识别综合测试

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |

**请求示例：**

```bash
GET http://localhost:8080/test/hotspot/detect?bizType=product&bizKey=12345
```

**请求说明：**
- 该请求会先记录一次访问统计，然后基于统计结果识别热点等级
- 综合测试了访问统计和热点识别两个模块

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "12345",
  "countShort": 1,
  "countLong": 1,
  "hotspotLevel": "COLD",
  "hotspotDescription": "冷数据，偶发访问，无需缓存",
  "threshold": 5
}
```

**响应说明：**
- `countShort`：短时间窗口（1秒）内的访问次数
- `countLong`：长时间窗口（60秒）内的访问次数
- `hotspotLevel`：识别出的热点等级（COLD/WARM/HOT/EXTREMELY_HOT）
- `hotspotDescription`：热点等级的详细描述
- `threshold`：当前热点等级对应的阈值上限

---

### 2.2 模拟热点场景

**接口地址：** `GET /test/hotspot/simulate`

**描述：** 模拟不同热度场景，验证热点识别准确性

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |
| scenario | String | 否 | WARM | 场景（COLD/WARM/HOT/EXTREMELY_HOT） |

**请求示例：**

```bash
GET http://localhost:8080/test/hotspot/simulate?bizType=product&bizKey=12345&scenario=HOT
```

**请求说明：**
- `scenario=HOT`：指定要模拟的热点场景为 HOT（高频热点）
- 系统会自动生成符合该场景的访问次数（HOT 场景为 25 次）
- 用于验证热点识别算法的准确性

**响应示例：**

```json
{
  "scenario": "HOT",
  "simulateCount": 25,
  "bizType": "product",
  "bizKey": "12345",
  "countShort": 25,
  "countLong": 25,
  "detectedLevel": "HOT",
  "expectedLevel": "HOT",
  "matched": true,
  "durationMs": 125
}
```

**响应说明：**
- `simulateCount`：为达到目标场景而生成的访问次数
- `detectedLevel`：实际检测到的热点等级
- `expectedLevel`：期望的热点等级（等于请求的 scenario）
- `matched`：检测结果是否与预期匹配（true 表示识别正确）
- `durationMs`：模拟过程总耗时

---

### 2.3 查看热点阈值

**接口地址：** `GET /test/hotspot/thresholds`

**描述：** 查看当前热点阈值配置

**请求参数：** 无

**请求示例：**

```bash
GET http://localhost:8080/test/hotspot/thresholds
```

**请求说明：**
- 该接口不需要参数，直接返回系统配置的热点阈值
- 用于了解热点等级的判定标准

**响应示例：**

```json
{
  "COLD": {
    "threshold": 5,
    "description": "冷数据，偶发访问"
  },
  "WARM": {
    "threshold": 20,
    "description": "中等热度，稳定访问"
  },
  "HOT": {
    "threshold": 100,
    "description": "高频热点"
  },
  "EXTREMELY_HOT": {
    "threshold": 2147483647,
    "description": "极热数据，突发流量"
  }
}
```

**响应说明：**
- `threshold`：热点等级的阈值上限（访问次数/秒）
- 热点等级判定规则：
  - 访问次数 < 5 → COLD（冷数据）
  - 5 ≤ 访问次数 < 20 → WARM（温数据）
  - 20 ≤ 访问次数 < 100 → HOT（热数据）
  - 访问次数 ≥ 100 → EXTREMELY_HOT（极热数据）

---

## 3. StrategyTestController - 策略决策引擎测试

**基础路径：** `/test/strategy`

### 3.1 完整流程测试

**接口地址：** `GET /test/strategy/full`

**描述：** 完整流程测试（访问统计 → 热点识别 → 策略决策）

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |

**请求示例：**

```bash
GET http://localhost:8080/test/strategy/full?bizType=product&bizKey=12345
```

**请求说明：**
- 该接口测试完整的三段流程：访问统计 → 热点识别 → 策略决策
- 展示了从原始访问到最终缓存策略的完整转换过程

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "12345",
  "statResult": {
    "countShort": 1,
    "countLong": 1
  },
  "hotspotLevel": "COLD",
  "decision": {
    "cacheMode": "NONE",
    "ttlLevel": "SHORT"
  }
}
```

**响应说明：**
- `statResult`：访问统计结果（第一阶段输出）
- `hotspotLevel`：热点识别结果（第二阶段输出）
- `decision`：策略决策结果（第三阶段输出）
  - `cacheMode`：推荐的缓存模式
  - `ttlLevel`：推荐的 TTL 等级
- 示例中因为访问次数为 1（COLD），所以决策为不使用缓存（NONE）

---

### 3.2 直接测试策略决策

**接口地址：** `GET /test/strategy/decide`

**描述：** 直接测试策略决策（指定热点等级）

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| level | String | 是 | - | 热点等级（COLD/WARM/HOT/EXTREMELY_HOT） |

**请求示例：**

```bash
GET http://localhost:8080/test/strategy/decide?level=HOT
```

**请求说明：**
- `level=HOT`：直接指定热点等级为 HOT
- 跳过统计和识别环节，直接测试策略决策引擎
- 用于验证不同热点等级对应的缓存策略

**响应示例：**

```json
{
  "inputLevel": "HOT",
  "cacheMode": "LOCAL_AND_REMOTE",
  "ttlLevel": "LONG",
  "description": "本地缓存 + Redis 双层缓存，长 TTL"
}
```

**响应说明：**
- `inputLevel`：输入的热点等级（回显）
- `cacheMode`：决策的缓存模式
  - HOT 等级 → 使用双层缓存（本地 + Redis）
- `ttlLevel`：决策的 TTL 等级
  - HOT 等级 → 使用长 TTL，减少回源频率
- `description`：决策结果的人类可读描述

---

### 3.3 查看策略映射

**接口地址：** `GET /test/strategy/mappings`

**描述：** 查看所有热点等级对应的策略配置

**请求参数：** 无

**请求示例：**

```bash
GET http://localhost:8080/test/strategy/mappings
```

**请求说明：**
- 该接口展示完整的热点等级到缓存策略的映射关系
- 用于理解系统的策略决策规则

**响应示例：**

```json
{
  "COLD": {
    "cacheMode": "NONE",
    "ttlLevel": "SHORT",
    "description": "不使用缓存，直接回源"
  },
  "WARM": {
    "cacheMode": "REMOTE_ONLY",
    "ttlLevel": "NORMAL",
    "description": "仅使用 Redis（L2），短期缓存"
  },
  "HOT": {
    "cacheMode": "LOCAL_AND_REMOTE",
    "ttlLevel": "LONG",
    "description": "本地缓存 + Redis 双层缓存，长 TTL"
  },
  "EXTREMELY_HOT": {
    "cacheMode": "LOCAL_ONLY",
    "ttlLevel": "LONG",
    "description": "仅使用本地缓存（L1）"
  }
}
```

**响应说明（策略设计思路）：**
- **COLD**：访问频率低，直接回源，避免缓存污染
- **WARM**：中等频率，使用 Redis 集中缓存，节省本地内存
- **HOT**：高频访问，使用双层缓存，本地缓存提供极速响应
- **EXTREMELY_HOT**：突发流量，仅用本地缓存，避免 Redis 压力过大

---

### 3.4 模拟场景测试

**接口地址：** `GET /test/strategy/simulate`

**描述：** 模拟不同热度场景，验证策略决策

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |
| scenario | String | 否 | WARM | 场景（COLD/WARM/HOT/EXTREMELY_HOT） |

**请求示例：**

```bash
GET http://localhost:8080/test/strategy/simulate?bizType=product&bizKey=12345&scenario=HOT
```

**请求说明：**
- `bizType=product`：指定业务类型为商品
- `bizKey=12345`：指定业务键为 12345
- `scenario=HOT`：指定要模拟的热点场景为 HOT（高频热点）
- 系统会自动生成 25 次访问（达到 HOT 阈值）来模拟该场景

**响应示例：**

```json
{
  "scenario": "HOT",
  "simulateCount": 25,
  "bizType": "product",
  "bizKey": "12345",
  "statResult": {
    "countShort": 25,
    "countLong": 25
  },
  "detectedLevel": "HOT",
  "decision": {
    "cacheMode": "LOCAL_AND_REMOTE",
    "ttlLevel": "LONG",
    "description": "本地缓存 + Redis 双层缓存，长 TTL"
  },
  "matched": true,
  "durationMs": 130
}
```

**响应说明：**
- `scenario`：请求的模拟场景（回显）
- `simulateCount`：为达到该场景生成的访问次数
- `bizType`：业务类型（回显）
- `bizKey`：业务键（回显）
- `statResult`：访问统计结果
  - `countShort`：短期窗口（1秒）的访问计数
  - `countLong`：长期窗口（60秒）的访问计数
- `detectedLevel`：实际检测到的热点等级
- `decision`：策略决策结果
  - `cacheMode`：推荐的缓存模式
  - `ttlLevel`：推荐的 TTL 等级
  - `description`：决策的详细描述
- `matched`：检测结果是否与预期场景匹配
- `durationMs`：模拟过程总耗时（毫秒）

---

## 4. CacheProxyTestController - 缓存访问代理测试

**基础路径：** `/test/cache`

### 4.1 测试缓存访问

**接口地址：** `GET /test/cache/access`

**描述：** 测试不同缓存模式的访问行为

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| key | String | 是 | - | 缓存键 |
| mode | String | 否 | LOCAL_AND_REMOTE | 缓存模式（NONE/LOCAL_ONLY/REMOTE_ONLY/LOCAL_AND_REMOTE） |
| ttl | String | 否 | NORMAL | TTL等级（SHORT/NORMAL/LONG） |

**请求示例：**

```bash
GET http://localhost:8080/test/cache/access?key=product:12345&mode=LOCAL_AND_REMOTE&ttl=NORMAL
```

**请求说明：**
- `key=product:12345`：要访问的缓存键
- `mode=LOCAL_AND_REMOTE`：使用双层缓存模式
- `ttl=NORMAL`：使用普通 TTL
- 首次访问会触发 DB 回源，并将数据写入缓存

**响应示例：**

```json
{
  "key": "product:12345",
  "value": "db-value-a3f2d8e1",
  "cacheMode": "LOCAL_AND_REMOTE",
  "ttlLevel": "NORMAL",
  "dbCalled": true,
  "totalDbCalls": 1,
  "durationMs": 52
}
```

**响应说明：**
- `key`：请求的缓存键（回显）
- `value`：实际获取到的数据值
- `cacheMode`：使用的缓存模式（回显）
- `ttlLevel`：使用的 TTL 等级（回显）
- `dbCalled`：本次请求是否触发了 DB 回源（true=缓存未命中）
- `totalDbCalls`：累计的 DB 访问次数（用于统计缓存效果）
- `durationMs`：本次请求总耗时（包括缓存查询和可能的 DB 查询）

---

### 4.2 测试缓存命中率

**接口地址：** `GET /test/cache/hit-test`

**描述：** 连续访问同一个 key，观察缓存效果

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| key | String | 是 | - | 缓存键 |
| mode | String | 否 | LOCAL_AND_REMOTE | 缓存模式 |
| count | Integer | 否 | 10 | 访问次数 |

**请求示例：**

```bash
GET http://localhost:8080/test/cache/hit-test?key=product:99999&mode=LOCAL_AND_REMOTE&count=10
```

**请求说明：**
- `count=10`：连续访问同一个 key 10 次
- 用于测试缓存命中率和性能
- 理想情况：第1次未命中回源，后9次全部命中缓存

**响应示例：**

```json
{
  "key": "product:99999",
  "cacheMode": "LOCAL_AND_REMOTE",
  "totalAccess": 10,
  "dbCalls": 1,
  "cacheHits": 9,
  "hitRate": "90.00%",
  "durationMs": 65,
  "avgLatencyMs": 6.5
}
```

**响应说明：**
- `key`：测试的缓存键（回显）
- `cacheMode`：使用的缓存模式（回显）
- `totalAccess`：总访问次数（应等于请求参数 count）
- `dbCalls`：实际的 DB 访问次数（理想情况为 1）
- `cacheHits`：缓存命中次数（totalAccess - dbCalls）
- `hitRate`：缓存命中率百分比
- `durationMs`：总耗时（毫秒）
- `avgLatencyMs`：平均每次访问延迟（命中缓存时延迟更低）

---

### 4.3 测试缓存失效

**接口地址：** `DELETE /test/cache/invalidate`

**描述：** 主动使缓存失效

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| key | String | 是 | - | 缓存键 |

**请求示例：**

```bash
DELETE http://localhost:8080/test/cache/invalidate?key=product:12345
```

**请求说明：**
- `key=product:12345`：指定要失效的缓存键
- 使用 DELETE 方法，表明是删除操作
- 会同时清除本地缓存和 Redis 中的数据

**响应示例：**

```json
{
  "key": "product:12345",
  "action": "invalidated",
  "durationMs": 3
}
```

**响应说明：**
- `key`：被失效的缓存键（回显）
- `action`：执行的操作类型（invalidated=已失效）
- `durationMs`：失效操作耗时（毫秒）

---

### 4.4 对比缓存模式性能

**接口地址：** `GET /test/cache/compare`

**描述：** 对比不同缓存模式的性能差异

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| key | String | 是 | - | 缓存键（会自动加后缀区分不同模式） |

**请求示例：**

```bash
GET http://localhost:8080/test/cache/compare?key=test:performance
```

**请求说明：**
- `key=test:performance`：指定测试用的缓存键
- 系统会对同一个 key 使用所有缓存模式各进行两次访问
- 用于对比不同缓存模式的性能差异

**响应示例：**

```json
{
  "NONE": {
    "firstAccessNs": 1250000,
    "secondAccessNs": 1230000,
    "speedup": "1.02x"
  },
  "LOCAL_ONLY": {
    "firstAccessNs": 1180000,
    "secondAccessNs": 45000,
    "speedup": "26.22x"
  },
  "REMOTE_ONLY": {
    "firstAccessNs": 1350000,
    "secondAccessNs": 280000,
    "speedup": "4.82x"
  },
  "LOCAL_AND_REMOTE": {
    "firstAccessNs": 1420000,
    "secondAccessNs": 42000,
    "speedup": "33.81x"
  }
}
```

**响应说明：**
每个缓存模式包含以下性能指标：
- `firstAccessNs`：第一次访问耗时（纳秒），通常未命中缓存需要回源
- `secondAccessNs`：第二次访问耗时（纳秒），应该命中缓存（NONE 除外）
- `speedup`：性能提升倍数（第一次耗时 ÷ 第二次耗时）

各模式性能分析：
- **NONE**：无缓存，两次耗时接近，speedup 约为 1x
- **LOCAL_ONLY**：本地缓存最快，speedup 约 26x
- **REMOTE_ONLY**：Redis 缓存，有网络开销，speedup 约 5x
- **LOCAL_AND_REMOTE**：双层缓存，第二次命中本地缓存，speedup 约 34x

---

### 4.5 并发访问测试

**接口地址：** `GET /test/cache/concurrent`

**描述：** 测试多线程并发访问场景

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| key | String | 是 | - | 缓存键 |
| threads | Integer | 否 | 5 | 并发线程数 |
| iterations | Integer | 否 | 20 | 每个线程的迭代次数 |

**请求示例：**

```bash
GET http://localhost:8080/test/cache/concurrent?key=product:hot&threads=5&iterations=20
```

**请求说明：**
- `key=product:hot`：并发访问的目标缓存键
- `threads=5`：启动 5 个并发线程
- `iterations=20`：每个线程各访问 20 次
- 总访问次数 = threads × iterations = 100 次
- 用于测试高并发场景下的缓存性能和线程安全性

**响应示例：**

```json
{
  "key": "product:hot",
  "threads": 5,
  "iterationsPerThread": 20,
  "totalAccess": 100,
  "dbCalls": 1,
  "cacheHits": 99,
  "hitRate": "99.00%",
  "durationMs": 245,
  "qps": 408
}
```

**响应说明：**
- `key`：测试的缓存键（回显）
- `threads`：并发线程数（回显）
- `iterationsPerThread`：每个线程的迭代次数（回显）
- `totalAccess`：总访问次数（threads × iterationsPerThread）
- `dbCalls`：实际的 DB 访问次数（理想情况为 1）
- `cacheHits`：缓存命中次数（totalAccess - dbCalls）
- `hitRate`：缓存命中率百分比
- `durationMs`：并发测试总耗时（毫秒）
- `qps`：每秒查询数（totalAccess × 1000 ÷ durationMs）

---

### 4.6 重置计数器

**接口地址：** `POST /test/cache/reset`

**描述：** 重置 DB 访问计数器

**请求参数：** 无

**请求示例：**

```bash
POST http://localhost:8080/test/cache/reset
```

**请求说明：**
- 该接口无需参数
- 用于重置测试环境，清除之前的 DB 访问计数

**响应示例：**

```json
{
  "previousCount": 156,
  "currentCount": 0
}
```

**响应说明：**
- `previousCount`：重置前的 DB 访问计数
- `currentCount`：重置后的计数（始终为 0）

---

## 5. EndToEndTestController - 端到端测试

**基础路径：** `/test/e2e`

### 5.1 完整流程测试

**接口地址：** `GET /test/e2e/full`

**描述：** 完整的调度流程测试（访问统计 → 热点识别 → 策略决策 → 缓存访问）

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型（PRODUCT/ORDER/USER等） |
| bizKey | String | 是 | - | 业务键 |

**请求示例：**

```bash
GET http://localhost:8080/test/e2e/full?bizType=product&bizKey=12345
```

**请求说明：**
- 该接口测试完整的端到端流程（四大模块集成）
- 流程：访问统计 → 热点识别 → 策略决策 → 缓存访问
- 提供一个真实的回源函数（模拟 DB 查询）

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "12345",
  "value": "db-value-12345-1707734521234",
  "hotspotLevel": "WARM",
  "dbCalled": true,
  "totalDbCalls": 1,
  "durationMs": 58
}
```

**响应说明：**
- `value`：最终获取到的数据（可能来自缓存或 DB）
- `hotspotLevel`：系统识别出的热点等级
- `dbCalled`：本次是否触发 DB 回源
- `totalDbCalls`：累计 DB 访问次数（用于评估缓存效果）
- `durationMs`：端到端总耗时（包含所有模块处理时间）

---

### 5.2 热点演化测试

**接口地址：** `GET /test/e2e/hotspot-evolution`

**描述：** 模拟从 COLD → WARM → HOT → EXTREMELY_HOT 的热点升级过程

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |

**请求示例：**

```bash
GET http://localhost:8080/test/e2e/hotspot-evolution?bizType=product&bizKey=99999
```

**请求说明：**
- 该接口模拟热点数据的演化过程
- 通过逐步增加访问次数，观察热点等级的动态变化
- 演化路径：COLD → WARM → HOT → EXTREMELY_HOT

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "99999",
  "evolution": [
    {
      "accessCount": 5,
      "hotspotLevel": "COLD"
    },
    {
      "accessCount": 10,
      "hotspotLevel": "WARM"
    },
    {
      "accessCount": 25,
      "hotspotLevel": "HOT"
    },
    {
      "accessCount": 50,
      "hotspotLevel": "HOT"
    },
    {
      "accessCount": 150,
      "hotspotLevel": "EXTREMELY_HOT"
    }
  ],
  "durationMs": 3245
}
```

**响应说明：**
- `evolution`：热点等级演化过程数组
- 每个阶段包含：
  - `accessCount`：累计访问次数
  - `hotspotLevel`：当前识别出的热点等级
- 演化过程展示了系统如何根据访问频率动态调整缓存策略
- 用于验证热点识别的阈值设置是否合理

---

### 5.3 缓存命中率测试

**接口地址：** `GET /test/e2e/cache-hit-rate`

**描述：** 连续访问同一个 key，观察缓存效果

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |
| count | Integer | 否 | 100 | 访问次数 |

**请求示例：**

```bash
GET http://localhost:8080/test/e2e/cache-hit-rate?bizType=product&bizKey=88888&count=100
```

**请求说明：**
- `bizType=product`：指定业务类型
- `bizKey=88888`：指定业务键
- `count=100`：连续访问 100 次
- 用于测试端到端流程的缓存效果和性能

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "88888",
  "totalAccess": 100,
  "dbCalls": 1,
  "cacheHits": 99,
  "hitRate": "99.00%",
  "hotspotLevel": "EXTREMELY_HOT",
  "durationMs": 520,
  "avgLatencyMs": "5.20"
}
```

**响应说明：**
- `bizType`：业务类型（回显）
- `bizKey`：业务键（回显）
- `totalAccess`：总访问次数（应等于请求参数 count）
- `dbCalls`：实际 DB 访问次数（理想情况为 1）
- `cacheHits`：缓存命中次数（totalAccess - dbCalls）
- `hitRate`：缓存命中率百分比
- `hotspotLevel`：系统识别出的热点等级（因访问频繁可能升级为 EXTREMELY_HOT）
- `durationMs`：总耗时（毫秒）
- `avgLatencyMs`：平均每次访问延迟（durationMs ÷ totalAccess）

---

### 5.4 并发访问测试

**接口地址：** `GET /test/e2e/concurrent`

**描述：** 多线程并发访问同一个 key

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 是 | - | 业务类型 |
| bizKey | String | 是 | - | 业务键 |
| threads | Integer | 否 | 10 | 并发线程数 |
| iterations | Integer | 否 | 50 | 每个线程的迭代次数 |

**请求示例：**

```bash
GET http://localhost:8080/test/e2e/concurrent?bizType=product&bizKey=77777&threads=10&iterations=50
```

**请求说明：**
- `bizType=product`：指定业务类型
- `bizKey=77777`：指定业务键
- `threads=10`：启动 10 个并发线程
- `iterations=50`：每个线程执行 50 次迭代
- 总访问次数 = 10 × 50 = 500 次
- 测试端到端流程在高并发场景下的表现

**响应示例：**

```json
{
  "bizType": "product",
  "bizKey": "77777",
  "threads": 10,
  "iterationsPerThread": 50,
  "totalAccess": 500,
  "dbCalls": 1,
  "cacheHits": 499,
  "hitRate": "99.80%",
  "durationMs": 1250,
  "qps": 400
}
```

**响应说明：**
- `bizType`：业务类型（回显）
- `bizKey`：业务键（回显）
- `threads`：并发线程数（回显）
- `iterationsPerThread`：每线程迭代次数（回显）
- `totalAccess`：总访问次数（threads × iterationsPerThread）
- `dbCalls`：实际 DB 访问次数
- `cacheHits`：缓存命中次数（totalAccess - dbCalls）
- `hitRate`：缓存命中率百分比
- `durationMs`：并发测试总耗时（毫秒）
- `qps`：每秒查询数（totalAccess × 1000 ÷ durationMs）

---

### 5.5 缓存失效测试

**接口地址：** `DELETE /test/e2e/invalidate`

**描述：** 使缓存和模拟数据库失效

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizKey | String | 是 | - | 业务键 |

**请求示例：**

```bash
DELETE http://localhost:8080/test/e2e/invalidate?bizKey=12345
```

**请求说明：**
- `bizKey=12345`：指定要失效的业务键
- 该操作会清除缓存并同时清除模拟数据库中的数据

**响应示例：**

```json
{
  "bizKey": "12345",
  "action": "invalidated",
  "durationMs": 5
}
```

**响应说明：**
- `bizKey`：被失效的业务键（回显）
- `action`：执行的操作（invalidated=已失效）
- `durationMs`：失效操作耗时（毫秒）

---

### 5.6 重置所有状态

**接口地址：** `POST /test/e2e/reset`

**描述：** 重置所有测试状态（DB计数器、模拟数据库）

**请求参数：** 无

**请求示例：**

```bash
POST http://localhost:8080/test/e2e/reset
```

**请求说明：**
- 该接口无需参数
- 重置所有测试状态，包括 DB 计数器和模拟数据库
- 用于开始新一轮测试前的环境清理

**响应示例：**

```json
{
  "previousDbCalls": 256,
  "previousDbSize": 15,
  "status": "reset"
}
```

**响应说明：**
- `previousDbCalls`：重置前的累计 DB 访问次数
- `previousDbSize`：重置前模拟数据库中的数据条数
- `status`：操作状态（reset=已重置）

---

## 6. RateLimitTestController - 限流测试

**基础路径：** `/test/ratelimit`

### 6.1 单次限流测试

**接口地址：** `GET /test/ratelimit/acquire`

**描述：** 对单次请求进行限流判断，验证当前限流规则是否生效。

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 否 | order | 业务类型（如：order、product、user），用于 bizType 维度限流 |
| bizKey | String | 否 | sku_001 | 业务键（如：商品ID、用户ID），用于 bizKey 维度限流 |

**请求示例：**

```bash
GET http://localhost:8080/test/ratelimit/acquire?bizType=order&bizKey=sku_001
```

**请求说明：**
- 如果未超出任一维度限流阈值，请求将被允许通过。
- 如果超过全局 / bizType / bizKey 任一维度的限流阈值，将返回 HTTP 429，并在响应体中给出详细原因。

**成功响应示例（未触发限流）：**

```json
{
  "bizType": "order",
  "bizKey": "sku_001",
  "allowed": true,
  "costMs": 2,
  "message": "Request allowed"
}
```

**成功响应说明：**
- `allowed`：是否通过限流检查（true=通过）。
- `costMs`：本次限流检查耗时（毫秒）。
- `message`：结果描述信息。

**限流触发响应示例（HTTP 429）：**

```json
{
  "bizType": "order",
  "bizKey": "sku_001",
  "allowed": false,
  "dimension": "GLOBAL",
  "currentCount": 12000,
  "limit": 10000,
  "message": "Rate limit exceeded: dimension=GLOBAL, bizType=null, bizKey=null, current=12000, limit=10000"
}
```

**限流触发响应说明：**
- `dimension`：触发限流的维度（GLOBAL/BIZ_TYPE/BIZ_KEY）。
- `currentCount`：当前窗口内的实际请求数。
- `limit`：对应维度的限流阈值。
- `message`：详细的错误描述，便于排查问题。

---

### 6.2 并发压力测试

**接口地址：** `GET /test/ratelimit/pressure`

**描述：** 模拟高并发请求场景，统计通过/拒绝/异常的数量和整体 QPS，用于评估限流策略在高压场景下的表现。

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| bizType | String | 否 | order | 业务类型，用于 bizType 限流维度 |
| bizKey | String | 否 | sku_001 | 业务键，用于 bizKey 限流维度 |
| concurrency | Integer | 否 | 10 | 并发线程数 |
| requests | Integer | 否 | 100 | 每个线程发送的请求数 |

**请求示例：**

```bash
GET "http://localhost:8080/test/ratelimit/pressure?bizType=order&bizKey=sku_001&concurrency=20&requests=500"
```

**请求说明：**
- 总请求数 = `concurrency × requests`。
- 所有请求都会针对同一个 `bizType + bizKey` 组合，便于观察 bizKey 限流效果。

**响应示例：**

```json
{
  "bizType": "order",
  "bizKey": "sku_001",
  "totalRequests": 10000,
  "concurrency": 20,
  "allowedCount": 9500,
  "rejectedCount": 500,
  "errorCount": 0,
  "durationMs": 1200,
  "qps": 8333.33,
  "rejectRate": "5.00%"
}
```

**响应说明：**
- `totalRequests`：本次压测发送的总请求数（concurrency × requests）。
- `allowedCount`：通过限流检查的请求数量。
- `rejectedCount`：被限流拒绝的请求数量。
- `errorCount`：发生非限流异常的请求数量。
- `durationMs`：整个压测过程耗时（毫秒）。
- `qps`：本次压测期间的平均 QPS。
- `rejectRate`：拒绝率百分比（rejectedCount ÷ totalRequests）。

---

### 6.3 查看限流配置

**接口地址：** `GET /test/ratelimit/config`

**描述：** 查看当前限流模块的配置，包括各维度开关、QPS 阈值、窗口大小、Redis 容错策略等。

**请求参数：** 无

**请求示例：**

```bash
GET http://localhost:8080/test/ratelimit/config
```

**响应示例：**

```json
{
  "enableGlobalLimit": true,
  "enableBizTypeLimit": true,
  "enableBizKeyLimit": true,
  "globalQpsLimit": 10000,
  "bizTypeQpsLimit": 5000,
  "bizKeyQpsLimit": 1000,
  "windowSeconds": 1,
  "keyPrefix": "ratelimit",
  "failOpen": true,
  "redisTimeout": 100
}
```

**响应说明：**
- `enableGlobalLimit`：是否启用全局 QPS 限流。
- `enableBizTypeLimit`：是否启用 bizType 维度限流。
- `enableBizKeyLimit`：是否启用 bizKey 维度限流。
- `globalQpsLimit`：全局 QPS 限流阈值。
- `bizTypeQpsLimit`：单个 bizType 的 QPS 限流阈值。
- `bizKeyQpsLimit`：单个 bizKey 的 QPS 限流阈值。
- `windowSeconds`：限流统计窗口时长（秒）。
- `keyPrefix`：限流统计在 Redis 中使用的 Key 前缀。
- `failOpen`：Redis 异常时是否走 fail-open（true=默认放行，false=默认拒绝）。
- `redisTimeout`：限流相关 Redis 操作的超时时间（毫秒）。

---

### 6.4 测试限流维度

**接口地址：** `GET /test/ratelimit/dimensions`

**描述：** 分别验证三种限流维度（GLOBAL / BIZ_TYPE / BIZ_KEY）是否生效，辅助检查配置是否正确。

**请求参数：** 无

**请求示例：**

```bash
GET http://localhost:8080/test/ratelimit/dimensions
```

**响应示例：**

```json
{
  "globalDimension": {
    "allowed": 45,
    "rejected": 5
  },
  "bizTypeDimension": {
    "bizType": "test_biz_type_1707734521234",
    "allowed": 48,
    "rejected": 2
  },
  "bizKeyDimension": {
    "bizKey": "test_biz_key_1707734521234",
    "allowed": 47,
    "rejected": 3
  }
}
```

**响应说明：**
- `globalDimension`：全局维度限流的测试结果。
  - `allowed`：在全局维度下被允许的请求数。
  - `rejected`：在全局维度下被拒绝的请求数。
- `bizTypeDimension`：bizType 维度限流的测试结果。
  - `bizType`：本次测试使用的业务类型标识。
  - `allowed`：在该 bizType 下被允许的请求数。
  - `rejected`：在该 bizType 下被拒绝的请求数。
- `bizKeyDimension`：bizKey 维度限流的测试结果。
  - `bizKey`：本次测试使用的业务键标识。
  - `allowed`：在该 bizKey 下被允许的请求数。
  - `rejected`：在该 bizKey 下被拒绝的请求数。

---

## 附录

### A. 枚举类型说明

#### CacheMode（缓存模式）

| 枚举值 | 说明 |
|--------|------|
| NONE | 不使用缓存，直接回源 |
| LOCAL_ONLY | 仅使用本地缓存（L1） |
| REMOTE_ONLY | 仅使用 Redis 缓存（L2） |
| LOCAL_AND_REMOTE | 使用本地 + Redis 双层缓存 |

#### CacheTtlLevel（TTL等级）

| 枚举值 | 说明 |
|--------|------|
| SHORT | 短期 TTL |
| NORMAL | 正常 TTL |
| LONG | 长期 TTL |

#### HotspotLevel（热点等级）

| 枚举值 | 说明 | 典型阈值 |
|--------|------|----------|
| COLD | 冷数据，偶发访问 | < 5 次/秒 |
| WARM | 中等热度，稳定访问 | 5-20 次/秒 |
| HOT | 高频热点 | 20-100 次/秒 |
| EXTREMELY_HOT | 极热数据，突发流量 | > 100 次/秒 |

#### RequestType（请求类型）

| 枚举值 | 说明 |
|--------|------|
| PRODUCT | 商品相关请求 |
| ORDER | 订单相关请求 |
| USER | 用户相关请求 |

### B. 测试建议

1. **顺序测试：** 建议按照以下顺序测试各模块
   - StatTestController（访问统计基础功能）
   - HotspotTestController（热点识别功能）
   - StrategyTestController（策略决策功能）
   - CacheProxyTestController（缓存访问功能）
   - EndToEndTestController（端到端集成测试）

2. **环境要求：** 
   - 需要本地 Redis 服务运行
   - 建议使用默认端口 6379

3. **数据清理：** 
   - 测试前使用各 Controller 的 reset 接口清理数据
   - 避免历史数据影响测试结果

---

**文档版本：** v1.0  
**最后更新：** 2026-02-12
