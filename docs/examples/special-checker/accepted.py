# 倒序排列不同于 output.txt 的升序排列，但属于合法答案。
n = int(input())
print(" ".join(str(number) for number in range(n, 0, -1)))
