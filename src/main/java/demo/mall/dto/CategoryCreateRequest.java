package demo.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CategoryCreateRequest {
    @NotNull
    private Long parentId;
    @NotBlank
    private String name;

    /**
     * 排序值，客户端可以不传。
     *
     * ⚠️ 注意下面这两个字段的初始值（100 / 1）在「走 Jackson 反序列化」时【不会生效】。
     *    原因：Jackson 3 默认会把「多参构造器」推断成 properties-based creator，
     *    直接 new 出对象再把 JSON 里的字段填进去 —— 字段声明处的 = 100 根本没被执行。
     *    所以真正的业务默认值由 CategoryService.create() 兜底，这里只是给
     *    「纯 Java 手动 new」的场景留一个合理初值，别把它当接口默认值用。
     */
    private Integer sort = 100;

    /**
     * 状态：0=停用，1=正常。客户端可以不传，不传按 1 处理。
     *
     * 这里刻意【不加 @NotNull】：因为 Jackson 3 会跳过字段初始值，
     * 加了 @NotNull 就变成「客户端必须显式传 status」，和「不传按 1」的业务约定矛盾。
     * 兜底逻辑在 CategoryService.create()，那里才是权威。
     */
    private Integer status = 1;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public CategoryCreateRequest() {
    }

    public CategoryCreateRequest(Long parentId, String name, Integer sort, Integer status) {
        this.parentId = parentId;
        this.name = name;
        this.sort = sort;
        this.status = status;
    }
}
