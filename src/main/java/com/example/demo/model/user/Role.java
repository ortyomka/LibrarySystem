package com.example.demo.model.user;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Entity
public class Role {
    @Id
    @Column(name = "ROLE_ID")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Size(max = 25)
    @Column(name = "NAME")
    private String name;

    public Role(Integer id, String name) {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
