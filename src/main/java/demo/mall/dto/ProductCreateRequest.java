package demo.mall.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ProductCreateRequest {
    @NotBlank
    private String name;
    @NotNull
    @DecimalMin("0.01") private BigDecimal sell;
    @NotNull @Min(0) private Integer stock;
    private String imageUrl;
    @NotNull private Long categoryId;

    public ProductCreateRequest() {
    }

    public ProductCreateRequest(String name, BigDecimal sell, Integer stock, String imageUrl, Long categoryId) {
        this.name = name;
        this.sell = sell;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getSell() {
        return sell;
    }

    public void setSell(BigDecimal sell) {
        this.sell = sell;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
