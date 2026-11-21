package demo.mall.dto;

import java.util.ArrayList;
import java.util.List;

public class CategoryTreeNode {
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
    private List<CategoryTreeNode> children = new ArrayList<>();

    public CategoryTreeNode() {
    }

    public CategoryTreeNode(Long id, String name, Integer sort, Integer status, List<CategoryTreeNode> children) {
        this.id = id;
        this.name = name;
        this.sort = sort;
        this.status = status;
        this.children = children;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<CategoryTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeNode> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }

}
