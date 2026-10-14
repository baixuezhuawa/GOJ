# 重复输出 1，n 大于 1 时会因缺失其他整数而被判为 WA。
n = int(input())
print(" ".join("1" for _ in range(n)))
