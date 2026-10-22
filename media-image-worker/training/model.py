from __future__ import annotations

import torch
from torch import nn
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small


class TinyBrandDetector(nn.Module):
    """ImageNet-pretrained MobileNetV3 detector with a 24x24 output grid."""

    def __init__(self, pretrained: bool = True) -> None:
        super().__init__()
        backbone = mobilenet_v3_small(
            weights=MobileNet_V3_Small_Weights.DEFAULT if pretrained else None
        )
        self.features = backbone.features
        self.projection = nn.Sequential(
            nn.Conv2d(576, 96, kernel_size=1, bias=False),
            nn.BatchNorm2d(96),
            nn.SiLU(inplace=True),
        )
        self.head = nn.Sequential(
            nn.Conv2d(96, 96, kernel_size=3, padding=1, groups=96, bias=False),
            nn.BatchNorm2d(96),
            nn.SiLU(inplace=True),
            nn.Conv2d(96, 64, kernel_size=1),
            nn.SiLU(inplace=True),
            nn.Conv2d(64, 5, kernel_size=1),
        )
        nn.init.constant_(self.head[-1].bias[0], -4.0)
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        normalized = (image - self.mean) / self.std
        features = self.projection(self.features(normalized))
        features = nn.functional.interpolate(features, size=(24, 24), mode="bilinear", align_corners=False)
        return self.head(features)
