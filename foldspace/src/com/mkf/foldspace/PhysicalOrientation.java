package com.mkf.foldspace;

/** Display modes use natural dimensions; Find N2's inner panel is naturally landscape. */
final class PhysicalOrientation {
    static boolean landscape(int naturalWidth,int naturalHeight,int rotation){
        if(naturalWidth<=0 || naturalHeight<=0 || rotation<0 || rotation>3)return false;
        return rotation%2==0?naturalWidth>naturalHeight:naturalHeight>naturalWidth;
    }
    private PhysicalOrientation(){}
}
