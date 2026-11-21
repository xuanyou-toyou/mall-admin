package demo.mall.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageResult 分页对象验证")
class PageResultTest {

    @Test
    @DisplayName("① Jackson 序列化：totalPages/hasNext/hasPrev 必须出现在 JSON 里")
    void jsonSerialize() throws Exception {
        System.out.println();
        System.out.println("========== Jackson 序列化结果 ==========");

        PageResult<String> p = new PageResult<>(List.of("a", "b"), 35, 1, 10);
        String json = new ObjectMapper().writeValueAsString(p);

        System.out.println(json);
        System.out.println();

        boolean allOk = true;
        for (String f : new String[]{"list", "total", "page", "size", "totalPages", "hasNext", "hasPrev"}) {
            boolean has = json.contains("\"" + f + "\"");
            System.out.println(String.format("  %-12s %s", f, has ? "✅ 有" : "❌ 缺失"));
            allOk &= has;
        }
        assertTrue(allOk, "所有字段都应该出现在 JSON 里");
    }

    @Test
    @DisplayName("② totalPages 计算边界")
    void totalPagesBoundary() {
        System.out.println();
        System.out.println("========== totalPages 边界 ==========");
        int[][] cases = {
                {0, 10, 0},
                {1, 10, 1},
                {10, 10, 1},
                {11, 10, 2},
                {35, 10, 4},
                {30, 10, 3},
                {100, 100, 1},
                {101, 100, 2},
        };
        for (int[] c : cases) {
            PageResult<String> p = new PageResult<>(List.of(), c[0], 1, c[1]);
            int actual = p.getTotalPages();
            boolean ok = actual == c[2];
            System.out.println(String.format("  total=%-4d size=%-4d → %d%s",
                    c[0], c[1], actual, ok ? " ✅" : " ❌ 期望 " + c[2]));
            assertEquals(c[2], actual, "total=" + c[0] + " size=" + c[1]);
        }
    }

    @Test
    @DisplayName("③ hasNext / hasPrev 逻辑")
    void navLogic() {
        System.out.println();
        System.out.println("========== 导航逻辑（total=35, size=10 → 共 4 页）==========");
        System.out.printf("  %-6s %-10s %-10s%n", "页", "hasNext", "hasPrev");

        for (int page = 1; page <= 4; page++) {
            PageResult<String> p = new PageResult<>(List.of(), 35, page, 10);
            System.out.printf("  %-6d %-10s %-10s%n", page, p.isHasNext(), p.isHasPrev());
        }

        // 断言
        assertFalse(new PageResult<>(List.of(), 35, 1, 10).isHasNext() == false, "第1页应该有下一页");
        assertTrue(new PageResult<>(List.of(), 35, 1, 10).isHasNext());
        assertFalse(new PageResult<>(List.of(), 35, 1, 10).isHasPrev(), "第1页不该有上一页");
        assertTrue(new PageResult<>(List.of(), 35, 4, 10).isHasPrev(), "第4页应该有上一页");
        assertFalse(new PageResult<>(List.of(), 35, 4, 10).isHasNext(), "第4页不该有下一页");
    }

    @Test
    @DisplayName("④ size=0 不崩溃")
    void zeroSize() {
        System.out.println();
        System.out.println("========== size=0 边界 ==========");
        PageResult<String> p = new PageResult<>(List.of(), 35, 1, 0);
        int pages = p.getTotalPages();
        System.out.println("  totalPages = " + pages + "（期望 0，不抛异常）");
        assertEquals(0, pages);
        System.out.println("  ✅ 没有抛 ArithmeticException");
    }

    @Test
    @DisplayName("⑤ 空数据：total=0")
    void emptyData() {
        System.out.println();
        System.out.println("========== 空数据 ==========");
        PageResult<String> p = new PageResult<>(List.of(), 0, 1, 10);
        System.out.println("  total       = " + p.getTotal());
        System.out.println("  totalPages  = " + p.getTotalPages());
        System.out.println("  hasNext     = " + p.isHasNext());
        System.out.println("  hasPrev     = " + p.isHasPrev());
        assertEquals(0, p.getTotalPages());
        assertFalse(p.isHasNext());
    }
}
