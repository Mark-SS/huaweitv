package com.quiztv.guard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Procedurally generates grade-4 (人教版四年级) math questions so they never repeat.
 */
final class MathGenerator {
    private static final String CATEGORY = "数学";
    private final Random rnd = new Random();

    Question next() {
        switch (rnd.nextInt(17)) {
            case 0:
                return multiply3x2();
            case 1:
                return divideExact();
            case 2:
                return divideWithRemainder();
            case 3:
                return mixedOperations();
            case 4:
                return roundToTenThousand();
            case 5:
                return digitPlace();
            case 6:
                return decimalPointShift();
            case 7:
                return decimalAddSub();
            case 8:
                return unitConversion();
            case 9:
                return triangleThirdAngle();
            case 10:
                return triangleSides();
            case 11:
                return chickensAndRabbits();
            case 12:
                return treePlanting();
            case 13:
                return average();
            case 14:
                return productChange();
            case 15:
                return speedTimeDistance();
            default:
                return angleFacts();
        }
    }

    // ---------------------------------------------------------------- helpers

    private int between(int lo, int hi) {
        return lo + rnd.nextInt(hi - lo + 1);
    }

    private Question numeric(String text, long answer, String suffix, String explanation) {
        Set<Long> values = new LinkedHashSet<>();
        values.add(answer);
        int guard = 0;
        while (values.size() < 4 && guard++ < 200) {
            long candidate = distractor(answer);
            if (candidate >= 0 && candidate != answer) {
                values.add(candidate);
            }
        }
        long filler = answer + 1;
        while (values.size() < 4) {
            values.add(filler++);
        }
        List<Long> list = new ArrayList<>(values);
        Collections.shuffle(list, rnd);
        String[] options = new String[4];
        int answerIndex = 0;
        for (int i = 0; i < 4; i++) {
            options[i] = list.get(i) + suffix;
            if (list.get(i) == answer) {
                answerIndex = i;
            }
        }
        return new Question(CATEGORY, text, options, answerIndex, explanation);
    }

    private long distractor(long answer) {
        long magnitude = Math.max(1, (long) Math.pow(10, Math.max(0, String.valueOf(Math.abs(answer)).length() - 2)));
        switch (rnd.nextInt(7)) {
            case 0:
                return answer + between(1, 3);
            case 1:
                return answer - between(1, 3);
            case 2:
                return answer + magnitude * between(1, 3);
            case 3:
                return answer - magnitude * between(1, 3);
            case 4:
                return answer * 10;
            case 5:
                return answer / 10;
            default:
                return swapDigits(answer);
        }
    }

    private long swapDigits(long value) {
        if (value < 10) {
            return value + 5;
        }
        char[] digits = String.valueOf(value).toCharArray();
        int i = rnd.nextInt(digits.length - 1);
        char tmp = digits[i];
        digits[i] = digits[i + 1];
        digits[i + 1] = tmp;
        if (digits[0] == '0') {
            return value + 11;
        }
        return Long.parseLong(new String(digits));
    }

    private Question choice(String text, String correct, List<String> wrong, String explanation) {
        Set<String> set = new LinkedHashSet<>();
        set.add(correct);
        for (String w : wrong) {
            if (set.size() < 4) {
                set.add(w);
            }
        }
        String[] fillers = {"以上都不对", "无法确定", "条件不足"};
        for (int i = 0; set.size() < 4 && i < fillers.length; i++) {
            set.add(fillers[i]);
        }
        List<String> list = new ArrayList<>(set);
        Collections.shuffle(list, rnd);
        String[] options = list.toArray(new String[0]);
        int idx = list.indexOf(correct);
        return new Question(CATEGORY, text, options, idx, explanation);
    }

    private static String dec(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------ generators

    private Question multiply3x2() {
        int a = between(102, 998);
        int b = between(12, 98);
        long answer = (long) a * b;
        return numeric(a + " × " + b + " = ?", answer, "", a + " × " + b + " = " + answer);
    }

    private Question divideExact() {
        int divisor = between(11, 98);
        int quotient = between(11, 98);
        long dividend = (long) divisor * quotient;
        return numeric(dividend + " ÷ " + divisor + " = ?", quotient, "",
                dividend + " ÷ " + divisor + " = " + quotient);
    }

    private Question divideWithRemainder() {
        int divisor = between(12, 35);
        int quotient = between(6, 48);
        int remainder = between(1, divisor - 1);
        long dividend = (long) divisor * quotient + remainder;
        String correct = "商 " + quotient + " 余 " + remainder;
        List<String> wrong = new ArrayList<>();
        wrong.add("商 " + (quotient + 1) + " 余 " + remainder);
        wrong.add("商 " + quotient + " 余 " + Math.max(0, remainder - between(1, 3)) );
        wrong.add("商 " + (quotient - 1) + " 余 " + Math.min(divisor - 1, remainder + between(1, 4)));
        wrong.add("商 " + quotient + " 余 " + Math.min(divisor - 1, remainder + 1));
        return choice(dividend + " ÷ " + divisor + " 的结果是？", correct, wrong,
                divisor + " × " + quotient + " + " + remainder + " = " + dividend);
    }

    private Question mixedOperations() {
        int type = rnd.nextInt(4);
        int a;
        int b;
        int c;
        String text;
        long answer;
        String explain;
        switch (type) {
            case 0:
                a = between(20, 200);
                b = between(3, 12);
                c = between(3, 12);
                text = a + " + " + b + " × " + c + " = ?";
                answer = a + (long) b * c;
                explain = "先算乘法：" + b + " × " + c + " = " + (b * c) + "，再加 " + a;
                break;
            case 1:
                a = between(5, 60);
                b = between(5, 60);
                c = between(3, 12);
                text = "(" + a + " + " + b + ") × " + c + " = ?";
                answer = (long) (a + b) * c;
                explain = "先算括号里的：" + a + " + " + b + " = " + (a + b) + "，再乘 " + c;
                break;
            case 2:
                c = between(3, 12);
                b = c * between(4, 20);
                a = between(100, 500);
                text = a + " − " + b + " ÷ " + c + " = ?";
                answer = a - b / c;
                explain = "先算除法：" + b + " ÷ " + c + " = " + (b / c) + "，再用 " + a + " 减去它";
                break;
            default:
                a = between(12, 60);
                b = between(3, 12);
                c = between(10, Math.min(100, a * b - 1));
                text = a + " × " + b + " − " + c + " = ?";
                answer = (long) a * b - c;
                explain = "先算乘法：" + a + " × " + b + " = " + (a * b) + "，再减 " + c;
                break;
        }
        return numeric(text, answer, "", explain);
    }

    private Question roundToTenThousand() {
        long n = between(2, 9999) * 10000L + between(0, 9999);
        long answer = (n + 5000) / 10000;
        String formatted = String.format("%,d", n).replace(",", "");
        return numeric("把 " + formatted + " 四舍五入到万位，约是多少万？", answer, "万",
                "看千位上的数字：满 5 进一，不满 5 舍去，结果约 " + answer + " 万");
    }

    private Question digitPlace() {
        String[] places = {"个位", "十位", "百位", "千位", "万位", "十万位", "百万位", "千万位", "亿位"};
        int length = between(5, 9);
        StringBuilder sb = new StringBuilder();
        List<Integer> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            digits.add(i);
        }
        Collections.shuffle(digits, rnd);
        if (digits.get(0) == 0) {
            Collections.swap(digits, 0, 1);
        }
        for (int i = 0; i < length; i++) {
            sb.append(digits.get(i));
        }
        String number = sb.toString();
        int pos = rnd.nextInt(length);
        char digit = number.charAt(pos);
        int placeIndex = length - 1 - pos;
        String correct = places[placeIndex];
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < places.length && wrong.size() < 6; i++) {
            if (i != placeIndex && Math.abs(i - placeIndex) <= 3) {
                wrong.add(places[i]);
            }
        }
        Collections.shuffle(wrong, rnd);
        return choice("在数 " + number + " 中，数字 " + digit + " 在哪一位上？", correct, wrong,
                "从右往左数第 " + (placeIndex + 1) + " 位是" + correct);
    }

    private Question decimalPointShift() {
        double base = between(105, 9999) / 100.0;
        int kind = rnd.nextInt(4);
        String op;
        double answer;
        switch (kind) {
            case 0:
                op = "× 10";
                answer = base * 10;
                break;
            case 1:
                op = "× 100";
                answer = base * 100;
                break;
            case 2:
                op = "÷ 10";
                answer = base / 10;
                break;
            default:
                op = "÷ 100";
                answer = base / 100;
                break;
        }
        String correct = dec(answer);
        List<String> wrong = new ArrayList<>();
        wrong.add(dec(answer * 10));
        wrong.add(dec(answer / 10));
        wrong.add(dec(answer * 100));
        wrong.add(dec(answer / 100));
        wrong.remove(correct);
        Collections.shuffle(wrong, rnd);
        return choice(dec(base) + " " + op + " = ?", correct, wrong,
                "小数点向" + (kind < 2 ? "右" : "左") + "移动" + (kind % 2 == 0 ? "一" : "两") + "位");
    }

    private Question decimalAddSub() {
        double a = between(100, 9999) / 100.0;
        double b = between(100, 9999) / 100.0;
        if (rnd.nextBoolean()) {
            double sum = a + b;
            String correct = dec(sum);
            List<String> wrong = new ArrayList<>();
            wrong.add(dec(sum + 0.1));
            wrong.add(dec(sum - 0.1));
            wrong.add(dec(sum + 1));
            wrong.add(dec(sum - 0.01));
            wrong.remove(correct);
            return choice(dec(a) + " + " + dec(b) + " = ?", correct, wrong, "小数点对齐后相加，得 " + correct);
        }
        double big = Math.max(a, b);
        double small = Math.min(a, b);
        if (big == small) {
            big += 1.5;
        }
        double diff = big - small;
        String correct = dec(diff);
        List<String> wrong = new ArrayList<>();
        wrong.add(dec(diff + 0.1));
        wrong.add(dec(diff - 0.1 < 0 ? diff + 0.2 : diff - 0.1));
        wrong.add(dec(diff + 1));
        wrong.add(dec(diff + 0.01));
        wrong.remove(correct);
        return choice(dec(big) + " − " + dec(small) + " = ?", correct, wrong, "小数点对齐后相减，得 " + correct);
    }

    private Question unitConversion() {
        int k = between(2, 9);
        int extra = between(1, 9) * 100 + between(0, 9) * 10;
        switch (rnd.nextInt(8)) {
            case 0:
                return numeric(k + " 千米 = ? 米", k * 1000L, " 米", "1 千米 = 1000 米");
            case 1:
                return numeric(k + " 吨 " + extra + " 千克 = ? 千克", k * 1000L + extra, " 千克", "1 吨 = 1000 千克");
            case 2:
                return numeric(k + " 公顷 = ? 平方米", k * 10000L, " 平方米", "1 公顷 = 10000 平方米");
            case 3:
                return numeric(k + " 平方千米 = ? 公顷", k * 100L, " 公顷", "1 平方千米 = 100 公顷");
            case 4: {
                int m = between(5, 55);
                return numeric(k + " 时 " + m + " 分 = ? 分", k * 60L + m, " 分", "1 时 = 60 分");
            }
            case 5:
                return numeric(k + " 分米 = ? 厘米", k * 10L, " 厘米", "1 分米 = 10 厘米");
            case 6: {
                int meters = k * 1000 + extra;
                String correct = dec(meters / 1000.0) + " 千米";
                List<String> wrong = new ArrayList<>();
                wrong.add(dec(meters / 100.0) + " 千米");
                wrong.add(dec(meters / 10000.0) + " 千米");
                wrong.add(dec(meters / 1000.0 + 1) + " 千米");
                return choice(meters + " 米 = ? 千米", correct, wrong, "1000 米 = 1 千米，" + meters + " ÷ 1000 = " + dec(meters / 1000.0));
            }
            default: {
                int grams = k * 1000 + extra;
                String correct = dec(grams / 1000.0) + " 千克";
                List<String> wrong = new ArrayList<>();
                wrong.add(dec(grams / 100.0) + " 千克");
                wrong.add(dec(grams / 10000.0) + " 千克");
                wrong.add(dec(grams / 1000.0 + 0.5) + " 千克");
                return choice(grams + " 克 = ? 千克", correct, wrong, "1000 克 = 1 千克");
            }
        }
    }

    private Question triangleThirdAngle() {
        int a = between(20, 100);
        int b = between(20, 170 - a);
        int c = 180 - a - b;
        return numeric("一个三角形的两个角分别是 " + a + "° 和 " + b + "°，第三个角是多少度？", c, "°",
                "三角形内角和是 180°，180 − " + a + " − " + b + " = " + c);
    }

    private Question triangleSides() {
        int a = between(3, 10);
        int b = between(a, a + 6);
        int valid = a + b - 1;
        List<String> wrong = new ArrayList<>();
        wrong.add((a + b) + " 厘米");
        wrong.add((a + b + 3) + " 厘米");
        wrong.add((b - a > 0 ? b - a : a + b + 6) + " 厘米");
        return choice("三角形的两条边分别长 " + a + " 厘米和 " + b + " 厘米，第三条边可能是多长？",
                valid + " 厘米", wrong, "三角形任意两边之和大于第三边，第三边要大于 " + (b - a) + " 且小于 " + (a + b));
    }

    private Question chickensAndRabbits() {
        int chickens = between(2, 15);
        int rabbits = between(2, 15);
        int heads = chickens + rabbits;
        int legs = chickens * 2 + rabbits * 4;
        boolean askRabbits = rnd.nextBoolean();
        long answer = askRabbits ? rabbits : chickens;
        return numeric("鸡兔同笼，共有 " + heads + " 个头、" + legs + " 只脚，" + (askRabbits ? "兔" : "鸡") + "有多少只？",
                answer, " 只", "假设全是鸡：" + heads + " × 2 = " + (heads * 2) + " 只脚，多出的 " + (legs - heads * 2)
                        + " 只脚 ÷ 2 = " + rabbits + " 只兔，鸡有 " + chickens + " 只");
    }

    private Question treePlanting() {
        int[] gaps = {2, 3, 4, 5, 6, 8, 10};
        int gap = gaps[rnd.nextInt(gaps.length)];
        int segments = between(5, 30);
        int length = segments * gap;
        switch (rnd.nextInt(3)) {
            case 0:
                return numeric("一条小路长 " + length + " 米，每隔 " + gap + " 米栽一棵树，两端都栽，一共要栽多少棵？",
                        segments + 1, " 棵", "间隔数 = " + length + " ÷ " + gap + " = " + segments + "，两端都栽：棵数 = 间隔数 + 1");
            case 1:
                return numeric("一条小路长 " + length + " 米，每隔 " + gap + " 米栽一棵树，两端都不栽，一共要栽多少棵？",
                        segments - 1, " 棵", "两端都不栽：棵数 = 间隔数 − 1 = " + (segments - 1));
            default:
                return numeric("一个圆形水池周长 " + length + " 米，每隔 " + gap + " 米放一盆花，一共要放多少盆？",
                        segments, " 盆", "封闭图形：盆数 = 间隔数 = " + length + " ÷ " + gap);
        }
    }

    private Question average() {
        int count = between(3, 4);
        int avg = between(60, 98);
        int[] values = new int[count];
        int sumDelta = 0;
        for (int i = 0; i < count - 1; i++) {
            int delta = between(-8, 8);
            values[i] = avg + delta;
            sumDelta += delta;
        }
        values[count - 1] = avg - sumDelta;
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(values[i]);
            total += values[i];
        }
        return numeric("小明 " + count + " 次数学测验的成绩分别是 " + sb + " 分，平均成绩是多少分？", avg, " 分",
                "总分 " + total + " ÷ " + count + " = " + avg);
    }

    private Question productChange() {
        int a = between(12, 60);
        int b = between(12, 60);
        long product = (long) a * b;
        int[] factors = {2, 3, 4, 5, 10, 100};
        int k = factors[rnd.nextInt(factors.length)];
        if (rnd.nextBoolean()) {
            return numeric("已知 " + a + " × " + b + " = " + product + "，那么 (" + a + " × " + k + ") × " + b + " = ?",
                    product * k, "", "一个因数乘 " + k + "，另一个因数不变，积也乘 " + k);
        }
        return numeric("两个因数的积是 " + product + "，一个因数乘 " + k + "，另一个因数不变，现在的积是多少？",
                product * k, "", product + " × " + k + " = " + product * k);
    }

    private Question speedTimeDistance() {
        int[] speeds = {40, 45, 50, 60, 65, 70, 80, 90, 100, 120};
        int speed = speeds[rnd.nextInt(speeds.length)];
        int hours = between(2, 6);
        int distance = speed * hours;
        switch (rnd.nextInt(3)) {
            case 0:
                return numeric("一辆汽车每小时行 " + speed + " 千米，" + hours + " 小时行多少千米？", distance, " 千米",
                        "路程 = 速度 × 时间 = " + speed + " × " + hours);
            case 1:
                return numeric("一辆汽车 " + hours + " 小时行了 " + distance + " 千米，它每小时行多少千米？", speed, " 千米",
                        "速度 = 路程 ÷ 时间 = " + distance + " ÷ " + hours);
            default:
                return numeric("一辆汽车每小时行 " + speed + " 千米，行 " + distance + " 千米需要几小时？", hours, " 小时",
                        "时间 = 路程 ÷ 速度 = " + distance + " ÷ " + speed);
        }
    }

    private Question angleFacts() {
        int degree;
        String correct;
        List<String> wrong = new ArrayList<>();
        switch (rnd.nextInt(3)) {
            case 0:
                degree = between(1, 89);
                correct = "锐角";
                break;
            case 1:
                degree = between(91, 179);
                correct = "钝角";
                break;
            default:
                degree = 90;
                correct = "直角";
                break;
        }
        for (String s : new String[]{"锐角", "直角", "钝角", "平角", "周角"}) {
            if (!s.equals(correct)) {
                wrong.add(s);
            }
        }
        Collections.shuffle(wrong, rnd);
        return choice(degree + "° 的角是什么角？", correct, wrong, "小于 90° 是锐角，等于 90° 是直角，大于 90° 小于 180° 是钝角");
    }
}
