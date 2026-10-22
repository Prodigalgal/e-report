import type { MaterialCategory } from '@/types/api';

export type MaterialOption = {
  category: MaterialCategory;
  label: string;
  specifications: string[];
};

export const MATERIAL_OPTIONS: MaterialOption[] = [
  {
    category: 'OPTICAL_CABLE',
    label: '光缆',
    specifications: ['12芯', '24芯', '48芯', '96芯', '144芯', '288芯'],
  },
  {
    category: 'JOINT_BOX',
    label: '接头盒',
    specifications: ['12芯', '24芯', '48芯', '96芯', '144芯', '288芯'],
  },
  {
    category: 'SPLITTER',
    label: '分光器',
    specifications: ['1:4', '1:8', '1:16', '1:32', '1:64'],
  },
];

export function findMaterialOption(category: MaterialCategory) {
  return MATERIAL_OPTIONS.find((item) => item.category === category) || MATERIAL_OPTIONS[0];
}
