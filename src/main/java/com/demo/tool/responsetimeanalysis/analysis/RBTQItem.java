package com.demo.tool.responsetimeanalysis.analysis;

public class RBTQItem {

    private Integer id;
    private Long blocking;
    private Integer resource_id;
    private Integer blocking_processor_size;

    public RBTQItem(Integer id, Long blocking, Integer resource_id, Integer blocking_processor_size) {
        this.id = id;
        this.blocking = blocking;
        this.resource_id = resource_id;
        this.blocking_processor_size = blocking_processor_size;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getBlocking() {
        return blocking;
    }

    public void setBlocking(Long blocking) {
        this.blocking = blocking;
    }

    public Integer getResource_id() {
        return resource_id;
    }

    public void setResource_id(Integer resource_id) {
        this.resource_id = resource_id;
    }

    public Integer getBlocking_processor_size() {
        return blocking_processor_size;
    }

    public void setBlocking_processor_size(Integer blocking_processor_size) {
        this.blocking_processor_size = blocking_processor_size;
    }
}
