package de.rezfon.stock;

public class Product {
    public long id;
    public String category;
    public String brand;
    public String model;
    public String type;
    public String variant;
    public String color;
    public int stock;
    public int minStock;
    public int targetStock;

    public Product() {}

    public String displayName() {
        StringBuilder b = new StringBuilder();
        if (brand != null && !brand.isEmpty()) b.append(brand).append(" ");
        if (model != null) b.append(model);
        if (type != null && !type.isEmpty()) b.append(" • ").append(type);
        if (variant != null && !variant.isEmpty()) b.append(" • ").append(variant);
        if (color != null && !color.isEmpty()) b.append(" • ").append(color);
        return b.toString();
    }

    public int orderQty() {
        return Math.max(0, targetStock - stock);
    }

    public boolean isLow() {
        return stock <= minStock;
    }
}
