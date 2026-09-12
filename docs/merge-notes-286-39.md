# Kemzy #286 + #39 OOM fix

Base: 3b1bbcf19fd0500f77204945ebf18f26bd973d1b
Fix source: 541c2333e765dba686ac3c8be2cc4399ede013ae

The merge must preserve #286 as the authoritative application state and transplant only the disk-backed ONNX model loading change required to avoid Java-heap readBytes() allocations.
