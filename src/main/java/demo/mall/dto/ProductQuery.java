package demo.mall.dto;

import java.math.BigDecimal;

public class ProductQuery {
    private String keyword;         // 关键词，模糊匹配 name，可为 null
    private Long categoryId;        // 分类 id，精确匹配，可为 null
    private BigDecimal minPrice;    // 价格下限（含），可为 null
    private BigDecimal maxPrice;    // 价格上限（含），可为 null
    private Integer status;         // 0/1，可为 null 表示「全部」
    private Integer page = 1;       // 第几页，默认 1
    private Integer size = 10;      // 每页几条，默认 10




    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public ProductQuery() {
    }

    public ProductQuery(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer status, Integer page, Integer size) {
        this.keyword = keyword;
        this.categoryId = categoryId;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.status = status;
        this.page = page;
        this.size = size;
    }
}