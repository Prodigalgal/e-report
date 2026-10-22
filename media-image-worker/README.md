# 工单图片品牌脱敏 Worker

该组件处理新上传以及由 Backend 巡检重新发现的历史工单图片。新图片写入 `_quarantine` 并创建 PostgreSQL 任务；巡检通过既有上传会话唯一键幂等补齐历史附件关联。Worker 单并发领取任务、定位中国移动标识与品牌文字、局部模糊并重编码为无元数据 JPEG。只有任务进入 `READY` 后，安全派生文件才允许预览或下载，处理过程不阻塞用户报单或回单。原始上传文件始终保留在 `_quarantine` 或历史根目录，安全派生文件保留在 `_sanitized`；两者均使用数据库记录的相对路径，可在 VPS 的上传根目录按工单附件和脱敏任务追溯。系统不会把原图回退给前端展示。

## 运行时

- 镜像只包含 OpenCV、Pillow/HEIF 与 psycopg，不包含 PyTorch 或训练素材。
- ONNX 模型随不可变镜像发布，启动时必须通过 `IMAGE_REDACTION_MODEL_SHA256` 校验。
- 原图和安全文件使用不同目录；原图保留用于审计和人工取证，失败时关闭访问，不回退到原图。
- 默认单并发，Kubernetes 资源限制见 `deploy/dedicated-cloud/e-report-test.yaml`。
- `IMAGE_REDACTION_PADDING_RATIO` 默认 `0.05`，并由 `IMAGE_REDACTION_MAX_PADDING_PIXELS`（默认 `16`）限制检测框外扩；Worker 先合并真实重叠框，再做外扩，避免相邻目标造成大面积模糊。
- 脱敏目标严格限定为“中国移动”、`China Mobile` 及其相邻标识；普通文字、颜色、二维码、其他运营商品牌不会因为外观相似而触发脱敏。颜色不作为品牌证据。
- `IMAGE_REDACTION_OCR_ENABLED=true` 时启用 RapidOCR 二次语义确认；模型低于 `IMAGE_REDACTION_STANDALONE_MODEL_CONFIDENCE` 的候选只有在邻近 OCR 命中时才保留。`IMAGE_REDACTION_DENSE_SCAN=true` 仅在粗检漏检或数量下降时运行有限重叠窗口，避免无界 CPU 消耗。
- 相关运行时配置：`IMAGE_REDACTION_OCR_MAX_SIDE`（默认 `2000`）、`IMAGE_REDACTION_OCR_MIN_CONFIDENCE`（默认 `0.65`）、`IMAGE_REDACTION_STANDALONE_MODEL_CONFIDENCE`（默认 `0.90`）、`IMAGE_REDACTION_DENSE_SCAN`（默认关闭）。

## 本地训练与验证

训练素材清单、许可与用途记录在 `training/source_manifest.json`，下载后的内容摘要记录在 `training/source-lock.json`。二进制素材、合成数据和 checkpoint 不进入 Git；ONNX 模型及模型清单进入版本控制。

```powershell
python training/download_sources.py
python training/prepare_templates.py
python training/render_annotations.py
python training/train_detector.py
python training/export_model.py
python training/validate_model.py --model-version cmcc-brand-v2-focused --include-annotations --dense-scan --ocr-enabled --ocr-max-side 2000 --ocr-min-confidence 0.65 --standalone-model-confidence 0.90 --threshold 0.65 --maximum-inference-ms 2500
```

`validate_model.py` 会以未参与训练的真实旧版标识照片、当前标识合成场景、无目标建筑照片以及法国军旗、普通铁塔和 3G 手机等硬负样本执行硬门槛，并记录 OCR/密集扫描配置和 CPU 推理基准。验证失败时禁止构建候选 Worker 镜像。
