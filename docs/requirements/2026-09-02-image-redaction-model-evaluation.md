# 图片品牌脱敏模型评估与重训方案

## 结论

现有 `cmcc-brand-v1` 必须重训，但不建议直接把 3B 级视觉语言模型放进当前 VPS 的图片 Worker。当前误判来自检测模型的场景域偏移、训练集硬负样本不足和大框低分辨率推理；把框扩大或单纯调低阈值都会增加误伤，不能作为最终修复。

本期采用“两阶段确定性链路”：轻量检测模型负责候选框，颜色/形状/OCR 负责品牌确认，只有通过安全门控的候选才生成脱敏结果。VLM 只用于离线标注辅助、难例复核和候选模型对比，不参与生产环境的不可逆自动打码决策。

## 当前证据

测试库恢复了 3 组生产历史样本的原图与派生图，原图仍在测试 VPS 的 `_quarantine`，派生图在 `_sanitized`，没有修改生产库：

| 样本 | 旧结果 | 误判 | 当前精度门控结果 |
| --- | --- | --- | --- |
| `restore-sample-1` | 模糊整块设备铭牌 | 没有可确认的中国移动标识 | 不打码 |
| `restore-sample-2` | 模糊中央大区域 | 实际只应覆盖黄色标签上的中国移动/China Mobile 小区域 | 仅覆盖窄品牌区域 |
| `restore-sample-3` | 模糊设备下方按钮 | 按钮不是品牌标识 | 不打码 |

`cmcc-brand-v1.json` 中已有离线评估 precision 约 `0.219`、recall 约 `0.570`；这组指标只说明当前模型仍需重训，不能用 3 张样本推导生产总体准确率。当前门控是风险遏制，不是模型验收结论。

## 候选模型对比

| 候选 | 类型与规模 | 优点 | 主要问题 | 结论 |
| --- | --- | --- | --- | --- |
| 现有 MobileNetV3-Small + 自定义检测头 | 专用检测器，约 4 MB ONNX | 已接入 OpenCV DNN，CPU 约百毫秒级，易离线、易审计，Apache/PyTorch 依赖不进入 Worker | 现有训练集不足，细小文字和复杂背景泛化差 | **首选重训基线** |
| YOLO11n | 通用目标检测，2.6M 参数 | 有检测/分割/导出链路，官方 CPU ONNX 参考约 56 ms | 默认 COCO 类别与本业务无关；需要重新训练、改后处理；官方模型为 AGPL-3.0 或 Enterprise，需法务确认 | 可做离线 A/B，不直接引入生产 |
| Microsoft Florence-2-base | 0.23B 多任务视觉模型 | 支持目标检测、区域定位、OCR、分割；MIT；可作为标注/教师 | Seq2seq 生成式输出，延迟、内存和输出稳定性不适合不可逆脱敏 Worker | 离线标注辅助或难例复核 |
| Microsoft Phi-3.5-Vision | 4.2B VLM | 文本+图像理解能力强，MIT | 官方模型卡以 GPU 训练/推理环境为主；生成文本而非确定性检测框，远超当前 VPS 资源 | 不放入 Worker |
| Qwen2.5-VL-3B | 3B VLM | 支持视觉定位、框坐标和结构化 JSON，中文场景友好 | 生成式模型，显存/内存和延迟高；框输出仍需二次校验，不能替代确定性门控 | 只做离线教师/复核 |
| NVIDIA VILA1.5-3B / NVILA | 3B VLM，提供 4bit/AWQ/TensorRT 路径 | NVIDIA GPU/Jetson 生态好，适合边缘 GPU 视觉理解 | 仍是 VLM；当前 VPS 没有可用 NVIDIA GPU 部署条件，CPU 运行代价过高 | 若未来增加 GPU，再评估 |
| PaddleOCR PP-OCRv5 mobile | 专用 OCR 检测/识别 | 可对“中国移动”“China Mobile”等文字做确定性校验，有 mobile 配置 | OCR 不能单独识别无文字图标；CPU 参考耗时明显高于现有检测器 | **作为二阶段文字确认候选** |
| SmolVLM-256M | 0.256B 小型 VLM | Apache 2.0，单图可在低显存环境运行 | 仍输出文本/描述，中文品牌框定位稳定性未知；不是专用检测器 | 不作为生产打码器 |

上述规模、能力和许可信息以各项目当前官方模型卡/文档为准；实际吞吐必须在本项目相同图片集、CPU 配额和容器限制下重新测量，不能直接套用公开 benchmark。

## 推荐生产架构

1. **候选检测**：重训现有 MobileNetV3-Small 检测器；保留多尺度窗口和 ONNX/OpenCV DNN，模型输出仅表示候选区域。
2. **品牌确认**：对候选区域执行 HSV 颜色与连通组件检查；对疑似文字区域可选接入 PP-OCRv5 mobile，匹配“中国移动 / China Mobile / CMCC”等白名单词及其轻微 OCR 误差。
3. **几何安全门控**：限制候选框最大面积、长宽比、padding 和相邻框合并；只有检测置信度、品牌视觉证据、OCR/模板证据满足配置的 `2-of-3` 才自动脱敏。未通过的任务进入 `REVIEW_REQUIRED`，不得回退展示原图。
4. **结果与追溯**：原图继续只存 `_quarantine`，脱敏结果只存 `_sanitized/{jobId}.jpg`；任务记录模型版本、策略版本、候选框、确认信号和人工复核结论。
5. **灰度发布**：测试命名空间先以 shadow 模式跑新旧模型并保存差异，不改变用户展示；通过验收后再切换 Worker 镜像，保留上一不可变模型用于回滚。

## 重训数据与标注

- 正样本：当前中国移动彩色标识、旧版绿色标识、中文/英文品牌文字、横竖标签、低光、反光、透视、遮挡和不同手机压缩质量。
- 硬负样本：绿色/蓝色设备外壳、按钮、指示灯、二维码、线缆标签、门牌、广告牌、网络设备铭牌和大面积纯色背景；本次 `restore-sample-1/3` 必须进入硬负样本回归集。
- 标注单位：每个独立 logo/品牌文字一个框；文字与图标相邻时允许一个组合框，但禁止把设备主体作为框；无目标图片必须支持空标注。
- 切分方式：按拍摄场景、设备和手机型号分组切分 train/validation/test，避免同一张图的裁剪泄漏到验证集；真实图片和标注不进入 Git。
- 初始目标：至少 300 张真实正样本、600 张硬负样本；数据不足时先扩充硬负样本，不用合成图片数量掩盖域偏移。

## 验收门槛

在独立 hold-out 集上同时满足：

- precision ≥ 99.5%；
- false-positive rate < 0.5%，且三类硬负样本均不得出现整块误伤；
- recall ≥ 95%，小于图片短边 8% 的文字/图标召回率 ≥ 90%；
- 框 IoU ≥ 0.50；脱敏面积相对标注面积的中位数 ≤ 1.5，P95 ≤ 2.0；
- 现有 Worker CPU median ≤ 150 ms、P95 ≤ 500 ms（与当前单并发/容器配额一致）；
- 旧版标识、当前标识、无标识建筑/设备、低光/压缩/旋转回归集全部通过；
- 重复处理幂等、原图可追溯、结果文件可读，失败不得把原图返回前端。

## 训练与发布流程

1. 在本机 GPU 训练和导出 ONNX，记录数据集摘要、代码 commit、训练参数、模型 SHA-256 和许可证。
2. 用统一验证脚本输出 precision/recall/FPR/IoU/面积膨胀/CPU 延迟，并与 `cmcc-brand-v1` 做 A/B 对比。
3. 只将模型文件、manifest 和必要的回归标注摘要提交仓库；原始用户图片留在受控训练目录。
4. 构建新的三段式版本候选，测试命名空间 shadow -> canary -> 全量；测试通知、邮件、工单写入保持关闭。
5. 用户终测和生产备份完成后才允许生产切换；任何关键指标不达标都回滚到上一不可变 Worker 镜像，不覆盖旧模型。

## 当前实施状态

- 已完成临时精度门控和品牌颜色细化，三组恢复样本的误判被拦截，真实品牌区域只保留窄框。
- 已修复图片巡检固定第 0 页导致后续附件永远不被扫描的问题，改为 UUID v7 主键游标。
- 尚未将新的训练权重部署到测试或生产；在重训数据和 hold-out 指标达标前，不宣称模型升级完成。

## 参考资料

- [Microsoft Florence-2 模型卡](https://huggingface.co/microsoft/Florence-2-base)
- [Microsoft Phi-3.5-Vision 模型卡](https://huggingface.co/microsoft/Phi-3.5-vision-instruct)
- [Qwen2.5-VL-3B 模型卡](https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct)
- [NVIDIA VILA 官方仓库](https://github.com/NVlabs/VILA)
- [PaddleOCR PP-OCRv5 官方文档](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/algorithm/PP-OCRv5/PP-OCRv5.md)
- [Ultralytics YOLO11 官方文档](https://docs.ultralytics.com/models/yolo11)
- [Hugging Face SmolVLM-256M 模型卡](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct)
