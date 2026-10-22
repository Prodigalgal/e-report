export const MATERIAL_CATEGORIES = {
    OPTICAL_CABLE: 'OPTICAL_CABLE',
    JOINT_BOX: 'JOINT_BOX',
    SPLITTER: 'SPLITTER',
};

export const MATERIAL_OPTIONS = [
    {
        value: MATERIAL_CATEGORIES.OPTICAL_CABLE,
        label: '光缆',
        specifications: ['12芯', '24芯', '48芯', '96芯', '144芯', '288芯'],
    },
    {
        value: MATERIAL_CATEGORIES.JOINT_BOX,
        label: '接头盒',
        specifications: ['12芯', '24芯', '48芯', '96芯', '144芯', '288芯'],
    },
    {
        value: MATERIAL_CATEGORIES.SPLITTER,
        label: '分光器',
        specifications: ['1:4', '1:8', '1:16', '1:32', '1:64'],
    },
];

export const findMaterialOption = (category) =>
    MATERIAL_OPTIONS.find((option) => option.value === category) || MATERIAL_OPTIONS[0];

export const getMaterialCategoryLabel = (category) => findMaterialOption(category).label;
