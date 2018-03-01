package com.example.demo.model.document;

import javax.persistence.Entity;
import java.util.Date;

@Entity
public class JournalArticle{
    private Integer id;
    private String title;
    private String[] authors;
    private String editor;
    private Date dateOfPublishing;
    private String[] tags;
    private int price;
    private int count;
}
