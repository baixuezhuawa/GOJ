import sys

# 将选手输出转换成升序排列，交给框架与 output.txt 比对。
try:
    numbers = [int(token) for token in sys.stdin.read().split()]
except ValueError:
    # 选手输出格式错误属于 WA，不通过异常退出制造系统故障。
    print("INVALID")
else:
    print(" ".join(str(number) for number in sorted(numbers)))
