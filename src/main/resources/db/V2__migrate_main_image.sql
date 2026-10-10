-- Migration script: Migrate main images to Product.mainImage field
-- Run this AFTER deploying the new code (Hibernate will create main_image_id column)

-- Step 1: Migrate existing main images to the new FK column
-- Uses is_main = 1 for old data, or img_order = 0 as fallback
    UPDATE product p
    SET p.main_image_id = (
        SELECT i.image_id
        FROM image i
        WHERE i.product_id = p.product_id
          AND (i.is_main = 1 OR i.img_order = 0)
        ORDER BY i.is_main DESC
        LIMIT 1
    )
    WHERE p.main_image_id IS NULL;

-- Step 2: Create index for the new FK (if not already created by Hibernate)
-- CREATE INDEX IF NOT EXISTS idx_product_main_image ON product(main_image_id);

-- Step 3: (Optional) After verification, drop is_main column
-- ALTER TABLE image DROP COLUMN is_main;
