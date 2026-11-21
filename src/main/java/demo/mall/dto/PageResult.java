package demo.mall.dto;

import java.util.List;

public class PageResult<T> {
    private List<T> list;      // 当前页数据
    private long total;        // 总条数
    private int page;          // 当前页（从 1 开始）
    private int size;          // 每页条数

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
// 三个计算方法（不是字段）

    // 总页数
    public int getTotalPages() {
        if (size <= 0) return 0;                      // ③ 防除零
        return (int) ((total + size - 1) / size);     // ④ 标准写法
    }
    // 有没有下一页
    public boolean isHasNext() {
        return page < getTotalPages();
    }
    // 有没有上一页
    public boolean isHasPrev() {
        return page > 1;
    }
}