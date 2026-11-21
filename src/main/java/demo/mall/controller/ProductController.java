package demo.mall.controller;

import demo.mall.common.Result;
import demo.mall.dto.PageResult;
import demo.mall.dto.ProductCreateRequest;
import demo.mall.dto.ProductQuery;
import demo.mall.model.Product;
import demo.mall.service.FileService;
import demo.mall.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final FileService fileService;

    public ProductController(ProductService productService, FileService fileService) {
        this.productService = productService;
        this.fileService = fileService;
    }

    // ① 分页查询（公开，无需登录）
    @GetMapping
    public Result<PageResult<Product>> page(ProductQuery query) {
        return Result.ok(productService.page(query));
    }

    // ② 查详情（公开）
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    // ③ 新增（需登录）
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProductCreateRequest req) {
        Product product = new Product();
        product.setName(req.getName());
        product.setSell(req.getSell());
        product.setStock(req.getStock());
        product.setImageUrl(req.getImageUrl());
        product.setCategoryId(req.getCategoryId());
        productService.create(product);
        return Result.ok();
    }

    // ④ 上下架（需登录）
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateStatus(id, status);
        return Result.ok();
    }

    /**
     * ⑤ 上传商品图片（需登录）。
     *
     * ============================================================
     * 为什么这个接口必须登录（安全性，不是随手加的）
     * ============================================================
     *
     * 路径是 /api/products/upload，落在 /api/** 里，
     * 而 WebConfig 里 AuthInterceptor 的 addPathPatterns("/api/**") 覆盖了它，
     * 且 excludePathPatterns 里没有 /api/products/** —— 所以它被拦截器保护，
     * 不带 token 会直接 401，请求进不到这个方法。
     *
     * 为什么「上传」比「查询」更需要登录：
     *   查询是读，最坏情况是泄露数据；
     *   上传是写，会把攻击者可控的字节写进服务器磁盘、占用磁盘空间。
     *   匿名可写 = 任何人可以拿你的服务器当免费图床，甚至塞满磁盘。
     *   所以这里绝不能被加进 excludePathPatterns。
     *
     * ============================================================
     * 为什么 Controller 里没有一行校验代码
     * ============================================================
     *
     * 项目约定：Controller 只做转发，业务校验在 Service。
     * 文件大小、扩展名、路径安全这些检查全在 FileService.upload() 里，
     * 这样「HTTP 上传」和「将来别的地方调用 FileService」走的是同一套规则，
     * 不会出现「接口挡住了但内部调用没挡」的口子。
     *
     * @param file 表单字段名必须是 file（前端 FormData 的 key 要对应）
     * @return Result.ok(相对 URL)，例如 /uploads/2026/11/21/9f2c...e1.jpg
     */
    @Operation(summary = "上传商品图片",
            description = "multipart/form-data，字段名 file；需登录。"
                    + "仅允许 jpg/jpeg/png/gif/webp，≤5MB，返回可直接访问的相对 URL")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        // 注意这个 @PostMapping("/upload") 和上面的 @GetMapping("/{id}") 不会冲突：
        // 方法不同（POST vs GET），Spring 匹配时先按 HTTP 方法分组再匹配路径。
        return Result.ok(fileService.upload(file));
    }
}
