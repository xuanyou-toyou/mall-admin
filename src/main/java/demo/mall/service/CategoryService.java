package demo.mall.service;

import demo.mall.common.BizException;
import demo.mall.dao.CategoryDao;
import demo.mall.dao.ProductDao;
import demo.mall.dto.CategoryTreeNode;
import demo.mall.model.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    /** 新建分类时，客户端没传 sort 用的默认排序值（和 DTO 里声明的 100 保持一致） */
    private static final int DEFAULT_SORT = 100;

    /** 新建分类时，客户端没传 status 用的默认状态：1=正常（不是 0，0 是停用） */
    private static final int DEFAULT_STATUS = 1;

    private final CategoryDao categoryDao;
    private final ProductDao productDao;

    public CategoryService(CategoryDao categoryDao, ProductDao productDao) {
        this.categoryDao = categoryDao;
        this.productDao = productDao;
    }
//    - create(Category)   校验父分类存在
    public void create(Category category) {
        // ============================================================
        // 先补业务默认值，再做校验
        // ============================================================
        // 为什么默认值放在这里，而不是靠 DTO 的字段初始值？
        //   Jackson 3 默认会把「多参构造器」推断成 properties-based creator，
        //   反序列化时直接 new 对象再填 JSON 字段，跳过字段声明处的 = 100 / = 1。
        //   也就是说 DTO 里写的初始值在接口调用路径上是死的。
        //   把默认值写在这里，就完全不依赖 JSON 库怎么实现，换库也不会变。
        if (category.getSort() == null) {
            category.setSort(DEFAULT_SORT);
        }
        if (category.getStatus() == null) {
            category.setStatus(DEFAULT_STATUS);
        }

        boolean isRoot = category.getParentId() == null || category.getParentId() == 0L;
        if (!isRoot) {
            if (categoryDao.findById(category.getParentId()) == null) {
                throw new BizException("父分类不存在");
            }
        }
        if (category.getStatus() > 1 || category.getStatus() < 0) {
            throw new BizException("状态应该为0或1");
        }
        categoryDao.insert(category);
        log.info("分类添加成功, id={} name={}", category.getId(), category.getName());
    }
//    - tree()             上面的算法
    public List<CategoryTreeNode> tree() {
        List<Category> all = categoryDao.findAll();          // ① 一次查询
        // ② 按 parentId 分组：Map<父id, 子分类列表>
        Map<Long, List<Category>> byParent = all.stream()
            .collect(Collectors.groupingBy(Category::getParentId));

        // ③ 从 parentId=0 开始递归组装
        return build(byParent, 0L);
    }

    private List<CategoryTreeNode> build(Map<Long, List<Category>> byParent, Long parentId) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> {
                    CategoryTreeNode node = new CategoryTreeNode();
                    node.setId(c.getId());
                    node.setName(c.getName());
                    node.setSort(c.getSort());
                    node.setStatus(c.getStatus());
                    node.setChildren(build(byParent, c.getId()));   // ← 递归，但只查内存
                    return node;
                })
                .toList();
    }
//    - update(Category)   校验存在、校验不能自己当自己的父
    public void update(Category category) {
        boolean isRoot = category.getParentId() == null || category.getParentId() == 0L;
        if (!isRoot) {
            if (categoryDao.findById(category.getParentId()) == null) {
                throw new BizException("父分类不存在");
            }
        }
        Category sameName = categoryDao.findByName(category.getName());
        if (sameName != null && !sameName.getId().equals(category.getId())) {
            throw new BizException("该分类名已被占用");
        }
        if (categoryDao.findById(category.getId()) == null) {
            throw new BizException("分类不存在");
        }
        if (category.getParentId() != null && category.getParentId().equals(category.getId())) {
            throw new BizException("分类不能作为自己的父分类");
        }
        if (category.getStatus() == null || category.getStatus() > 1 || category.getStatus() < 0) {
            throw new BizException("状态应该为0或1");
        }
        categoryDao.update(category);
    }
//    - delete(Long)
    public void delete(Long id) {
         if (categoryDao.findById(id) == null) {
             throw new BizException("分类不存在");
         }
        if (categoryDao.countByParentId(id) > 0) {
            throw new BizException("该分类下还有子分类，不能删除");
        }
        if (productDao.countByCategoryId(id) > 0) {
            throw new BizException("该分类下还有商品，不能删除");
        }
        categoryDao.deleteById(id);
    }
}
