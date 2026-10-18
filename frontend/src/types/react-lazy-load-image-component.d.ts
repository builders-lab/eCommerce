declare module "react-lazy-load-image-component" {
    import * as React from "react";

    export interface LazyLoadImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
        effect?: string;
        threshold?: number;
        placeholderSrc?: string;
    }

    export const LazyLoadImage: React.FC<LazyLoadImageProps>;
}
