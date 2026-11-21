package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.dao.CategoryDao;
import demo.mall.dao.ProductDao;
import demo.mall.dto.PageResult;
import demo.mall.dto.ProductQuery;
import demo.mall.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductDao productDao;
    private final CategoryDao categoryDao;

    public ProductService(ProductDao productDao, CategoryDao categoryDao) {
        this.productDao = productDao;
        this.categoryDao = categoryDao;
    }

    /** 新增商品 */
    public void create(Product product) {
        if (categoryDao.findById(product.getCategoryId()) == null) {
            throw new BizException("商品分类不存在");
        }
        if (product.getSell().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("价格不能小于0");
        }
        if (product.getStock().compareTo(0) < 0) {
            throw new BizException("库存不能为负数");
        }
        if (product.getName().isBlank()) {
            throw new BizException("商品名不能为空");
        }
        productDao.insert(product);
        log.info("商品添加成功, id={} name={}", product.getId(), product.getName());
    }

    /** 分页查询 */
    public PageResult<Product> page(ProductQuery query) {
        // ① 只校验参数合法性（不依赖 total）
        int page = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        int size = (query.getSize() == null || query.getSize() < 1) ? 10 : query.getSize();
        if (size > 100) size = 100;

        // ② 查总数
        long total = productDao.count(query);

        // ③ 查数据（page 超范围时自然返回空列表，不报错）
        List<Product> list = productDao.search(query);

        // ④ 组装
        return new PageResult<>(list, total, page, size);
    }

    /** 查详情 */
    public Product detail(Long id) {
        Product p = productDao.findById(id);
        if (p == null) {
            throw new BizException("商品不存在");
        }
        return p;
    }

    /** 上下架 */
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态值只能是 0（下架）或 1（上架）");
        }
        Product product = productDao.findById(id);
        if(id != null && product != null) {
            product.setStatus(status);
            productDao.update(product);
        } else {
            throw new BizException("商品不存在");
        }
    }
}
