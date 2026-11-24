# 已归档验证结果

项目清理时将最终统计从临时构建目录复制到这里，保留优化日志中各项结论的依据。原有 10 份历史 JSON 保持不变；2026-11-24 追加第三轮验证、对照和源码清单。原始报告复制后逐文件校验，合并基线和对照文件注明了来源。
报告中记录的绝对路径和 `build/...` 目录属于当时执行环境；各用例的临时源码、汇编、字节码和 class 文件已清理，可使用测试入口重新生成。

| 文件 | 内容 |
| --- | --- |
| [mips-base/results.json](mips-base/results.json) | MIPS 正确性基线，54 个用例、四种组合 |
| [mips-base/submission.json](mips-base/submission.json) | 基线提交包、综合样例和当前源码的验证记录 |
| [mips-round1/results.json](mips-round1/results.json) | 第一轮优化的 62 个用例运行统计 |
| [mips-round1/comparison-to-backup.json](mips-round1/comparison-to-backup.json) | 第一轮与优化前版本的同集合对照 |
| [mips-round2/results.json](mips-round2/results.json) | 第二轮优化的 68 个用例运行统计 |
| [mips-round2/comparison-to-round1.json](mips-round2/comparison-to-round1.json) | 第二轮与第一轮相同 62 个用例的对照 |
| [mips-round2/integration.json](mips-round2/integration.json) | 提交包运行综合样例、原源码和循环样例的结果与栈帧对照 |
| [llvm/results.json](llvm/results.json) | LLVM 12 回归结果 |
| [llvm/official-results.json](llvm/official-results.json) | 历史公共测试记录 |
| [frontend/snapshot.json](frontend/snapshot.json) | 历史前端快照兼容性检查结果 |
| [mips-extreme/comparison.md](mips-extreme/comparison.md) | 第三轮 89 个用例的逐项 FinalCycles 对照，89 项均改善 |
| [mips-extreme/results.json](mips-extreme/results.json) | 本轮 89 个用例、四种组合和完整指令统计 |
| [mips-extreme/baseline-results.json](mips-extreme/baseline-results.json) | 使用“2026/11/24 backup-初步优化”备份重新运行的同集合基线 |
| [mips-extreme/comparison-to-initial.json](mips-extreme/comparison-to-initial.json) | 周期、指令、访存逐项对照；原 68 项和全 89 项分别汇总 |
| [mips-extreme/switches.json](mips-extreme/switches.json) | 18 种配置 × 13 个用例，234 项全部通过 |
| [mips-extreme/llvm-results.json](mips-extreme/llvm-results.json) | 独立 LLVM 阶段 39 项回归 |
| [mips-extreme/submission.json](mips-extreme/submission.json) | Java 17 提交包、源文件一致性和 16 次执行结果 |
| [mips-extreme/source-manifest.json](mips-extreme/source-manifest.json) | 本轮验证对应源码和仿真器的 SHA-256 |

当前测试入口仍将新结果写入 `build`，不会覆盖这里的历史记录：

```powershell
python tests/run-mips-tests.py --out build/mips-tests
python tests/run-llvm-tests.py
pwsh -NoProfile -File tests/run-semantic-tests.ps1
```

已清理的一次性或过时脚本为 `tools/write-llvm-report.py`、`tools/probe.ps1`、`tools/run-tests.ps1`、`tests/check-frontend-snapshot.py`。
常用打包、恢复、工具链准备、运行库重建和当前回归脚本继续保留。原脚本版本仍可在 `backups` 的源码快照中查阅。
