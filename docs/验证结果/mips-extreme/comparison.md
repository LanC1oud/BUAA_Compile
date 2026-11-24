# 第三轮逐用例 FinalCycles 对照

两版均开启优化，课程 MARS 均关闭延迟槽。对照对象是修改前已恢复并重新运行的备份，未使用排行榜。

| 用例 | 备份版 | 本轮 | 降幅 |
| --- | ---: | ---: | ---: |
| minimal | 9 | 8 | 11.11% |
| exit-status | 9 | 8 | 11.11% |
| arithmetic | 41 | 32 | 21.95% |
| int-boundaries | 59 | 34 | 42.37% |
| casts-wrap | 114 | 44 | 61.40% |
| char-steps | 49 | 31 | 36.73% |
| runtime-char | 56 | 37 | 33.93% |
| mutable-global | 42 | 29.5 | 29.76% |
| shadow | 67 | 25.5 | 61.94% |
| recursion | 289.5 | 172.5 | 40.41% |
| param-copy | 106 | 31 | 70.75% |
| array-params | 136 | 77.5 | 43.01% |
| const-arrays | 120 | 55 | 54.17% |
| string-lengths | 111 | 64 | 42.34% |
| char-array-forward | 59 | 28.5 | 51.69% |
| printf-eval | 45 | 29.5 | 34.44% |
| printf-order | 63 | 36.5 | 42.06% |
| escapes | 41 | 37 | 9.76% |
| six-runtime | 56 | 45 | 19.64% |
| string-zero-one | 64 | 57 | 10.94% |
| string-truncate | 18 | 17 | 5.56% |
| string-crlf | 18 | 17 | 5.56% |
| comparisons | 30 | 29 | 3.33% |
| char-comparison-chain | 15 | 14 | 6.67% |
| short-circuit | 56 | 29.5 | 47.32% |
| while-short-circuit | 194 | 88 | 54.64% |
| loop-init | 221.5 | 64.5 | 70.88% |
| while-empty-nested | 583 | 181 | 68.95% |
| return-tail | 122 | 31 | 74.59% |
| switch-once | 67 | 37 | 44.78% |
| switch-default-middle | 146.5 | 62 | 57.68% |
| switch-default-first | 24 | 14 | 41.67% |
| switch-char | 24 | 18.5 | 22.92% |
| nested-control | 199.5 | 79 | 60.40% |
| static-scalar | 83 | 46.5 | 43.98% |
| static-array-char | 171 | 96.5 | 43.57% |
| static-shadow | 113 | 77.5 | 31.42% |
| static-recursive | 221 | 150 | 32.13% |
| many-args | 155 | 11 | 92.90% |
| mixed-args | 176 | 58.5 | 66.76% |
| recursive-stack-args | 701.5 | 390.5 | 44.33% |
| nested-calls | 296 | 11 | 96.28% |
| large-stack | 114 | 64 | 43.86% |
| global-alignment | 89 | 57 | 35.96% |
| global-last-element | 80 | 54 | 32.50% |
| string-consecutive | 43 | 36 | 16.28% |
| string-capacity-consumes | 77 | 72 | 6.49% |
| string-empty-eof | 43 | 36 | 16.28% |
| int-overflow | 64 | 33 | 48.44% |
| high-byte-data | 107 | 69 | 35.51% |
| array-sort | 2827.5 | 885 | 68.70% |
| far-branch-false | 28 | 23 | 17.86% |
| far-branch-true | 23429 | 23 | 99.90% |
| opt-signed-div-rem | 23513 | 15826 | 32.69% |
| opt-char-div-rem | 1805 | 495.5 | 72.55% |
| opt-loop-sum | 45531 | 13519 | 70.31% |
| opt-alias-call | 88 | 46.5 | 47.16% |
| opt-large-immediates | 59 | 11 | 81.36% |
| opt-multiply-powers | 264 | 91.5 | 65.34% |
| opt-calls-and-cache | 113 | 38.5 | 65.93% |
| opt-byte-alias | 89 | 58.5 | 34.27% |
| round2-register-pressure | 260 | 150 | 42.31% |
| round2-pressure-across-call | 307 | 142.5 | 53.58% |
| round2-dynamic-branches | 1195.5 | 812 | 32.08% |
| round2-slot-loop | 26281 | 6469 | 75.39% |
| round2-many-parameters | 319 | 74 | 76.80% |
| round2-char-pressure | 251 | 54 | 78.49% |
| extreme-magic-signed | 69838.5 | 42111.5 | 39.70% |
| extreme-magic-char | 192667 | 106389 | 44.78% |
| extreme-phi-cycle | 3293.5 | 843.5 | 74.39% |
| extreme-loop-pressure | 9574 | 3489 | 63.56% |
| extreme-inline-returns | 1083 | 241.5 | 77.70% |
| extreme-inline-loop-phi | 8688 | 1845 | 78.76% |
| extreme-memory-alias | 123 | 58.5 | 52.44% |
| extreme-string-store-order | 41 | 32 | 21.95% |
| extreme-gcm-loop | 37666.5 | 5392 | 85.68% |
| extreme-gcm-zero-trip | 42 | 26 | 38.10% |
| extreme-phi-call | 11501 | 5518 | 52.02% |
| extreme-char-phi | 1741 | 365.5 | 79.01% |
| extreme-tail-recursion | 815.5 | 133 | 83.69% |
| extreme-tail-void | 312.5 | 73 | 76.64% |
| extreme-tail-multiple | 1347.5 | 339 | 74.84% |
| extreme-global-scalar | 2199 | 298 | 86.45% |
| extreme-array-parameter | 515 | 174.5 | 66.12% |
| extreme-range-positive | 7715 | 2392 | 69.00% |
| extreme-range-overflow | 3606 | 1394 | 61.34% |
| extreme-range-negative | 2212 | 840 | 62.03% |
| extreme-range-step | 879 | 209 | 76.22% |
| official-comprehensive | 3279.5 | 1094 | 66.64% |

89 项合计：491288 → 214804，下降 56.28%。
其中原有 68 项：135428 → 42639.5，下降 68.52%。
逐项：89 改善、0 持平、0 退化。关闭优化的指令统计逐项完全一致。

合计只用于描述此集合；各项差异及动态指令、访存计数详见 comparison-to-initial.json。
