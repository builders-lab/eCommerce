package com.ecommerce.platform.modules.catalog.repository;

import com.ecommerce.platform.modules.catalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p")
    Page<Product> findPage(Pageable pageable);

    @Query("select p from Product p where p.category = :category")
    Page<Product> findPageByCategory(@Param("category") String category, Pageable pageable);

    @EntityGraph(attributePaths = {"images"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"discounts"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForCartMutationById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"discounts"})
    @Query("select distinct p from Product p join p.discounts d where d.id in :discountIds")
    List<Product> findProductsWithDiscountIds(@Param("discountIds") Collection<Long> discountIds);

    @Query("""
            select new com.ecommerce.platform.modules.catalog.repository.ProductImageRow(p.id, image)
            from Product p
            join p.images image
            where p.id in :productIds
            """)
    List<ProductImageRow> findImageRowsByProductIds(@Param("productIds") List<Long> productIds);

    List<Product> findByCategory(String category);

    @Query("select distinct p.category from Product p where p.category is not null order by p.category")
    List<String> findDistinctCategories();
}
