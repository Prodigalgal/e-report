import React, { useEffect } from 'react';
import {
    Box,
    Button,
    Container,
    Divider,
    Stack,
    Typography,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import LoginIcon from '@mui/icons-material/Login';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import { Link as RouterLink } from 'react-router-dom';
import BrandLogo from '../../components/Brand/BrandLogo';

const features = [
    {
        icon: <AssignmentTurnedInIcon />,
        title: '工单闭环管理',
        description: '支持网络故障、箱门未关和破损箱体更换的报单、处理、回单与关闭。',
    },
    {
        icon: <LocationOnIcon />,
        title: '现场精准定位',
        description: '采集现场定位坐标，自动填写地址并可直接唤起地图导航。',
    },
    {
        icon: <CameraAltIcon />,
        title: '现场材料留痕',
        description: '报单和回单均可上传图片或视频，记录故障现状、处理结果及物料消耗。',
    },
    {
        icon: <QueryStatsIcon />,
        title: '进度与数据分析',
        description: '按权限查询工单状态、处理进度和分类统计，便于运维管理与过程追踪。',
    },
];

const workflow = [
    '账号登录与权限校验',
    '获取定位并核对现场地址',
    '选择工单类型并填写现场信息',
    '上传图片或视频后提交报单',
    '维护人员接单并导航到现场',
    '拍摄处理结果、登记物料并回单',
    '工单关闭并进入统计分析',
];

function AppInfoPage() {
    useEffect(() => {
        document.title = '网络运维管理 - 安卓应用';
    }, []);

    return (
        <Box sx={{ minHeight: '100vh', bgcolor: '#F8FAFC', color: '#172033' }}>
            <Box component="header" sx={{ bgcolor: '#FFFFFF', borderBottom: '1px solid #E2E8F0' }}>
                <Container maxWidth="lg" sx={{ minHeight: 72, display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <BrandLogo size={40} />
                    <Typography variant="h6" component="div" sx={{ fontWeight: 800 }}>
                        网络运维管理
                    </Typography>
                    <Box sx={{ flex: 1 }} />
                    <Button component={RouterLink} to="/login" startIcon={<LoginIcon />}>
                        进入系统
                    </Button>
                </Container>
            </Box>

            <Box component="main">
                <Box sx={{ bgcolor: '#FFFFFF', py: { xs: 5, md: 8 }, borderBottom: '1px solid #E2E8F0' }}>
                    <Container maxWidth="lg">
                        <Stack spacing={2.5} sx={{ maxWidth: 780 }}>
                            <Typography component="h1" variant="h2" sx={{ fontWeight: 850, fontSize: { xs: '2rem', md: '3rem' } }}>
                                网络运维管理
                            </Typography>
                            <Typography variant="h6" color="text.secondary" sx={{ lineHeight: 1.8, fontWeight: 400 }}>
                                面向通信装维人员的现场工单管理应用，覆盖故障上报、现场定位、工单处置、回单留痕和数据分析全过程。
                            </Typography>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pt: 1 }}>
                                <Button
                                    component="a"
                                    href="/download/android"
                                    variant="contained"
                                    size="large"
                                    startIcon={<DownloadIcon />}
                                >
                                    下载原生版
                                </Button>
                                <Button component="a" href="/download/android-lite" variant="contained" color="secondary" size="large" startIcon={<DownloadIcon />}>
                                    下载 Lite 轻量版
                                </Button>
                                <Button component={RouterLink} to="/login" variant="outlined" size="large" startIcon={<LoginIcon />}>
                                    进入网页系统
                                </Button>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">
                                原生版支持 OTA 功能更新；Lite 需要安卓 8.0 及以上，网页功能自动更新。两款分别升级，切换版本请先卸载当前客户端，再下载另一款安装。
                            </Typography>
                        </Stack>
                    </Container>
                </Box>

                <Container maxWidth="lg" sx={{ py: { xs: 4, md: 7 } }}>
                    <Typography component="h2" variant="h4" sx={{ fontWeight: 800, mb: 3 }}>
                        核心功能
                    </Typography>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
                        {features.map(feature => (
                            <Box key={feature.title} sx={{ bgcolor: '#FFFFFF', border: '1px solid #E2E8F0', borderRadius: 1, p: 2.5 }}>
                                <Box sx={{ color: 'primary.main', mb: 1 }}>{feature.icon}</Box>
                                <Typography component="h3" variant="h6" sx={{ fontWeight: 750, mb: 0.75 }}>
                                    {feature.title}
                                </Typography>
                                <Typography color="text.secondary" sx={{ lineHeight: 1.75 }}>
                                    {feature.description}
                                </Typography>
                            </Box>
                        ))}
                    </Box>

                    <Divider sx={{ my: { xs: 4, md: 6 } }} />

                    <Typography component="h2" variant="h4" sx={{ fontWeight: 800, mb: 3 }}>
                        运行流程
                    </Typography>
                    <Box component="ol" sx={{ m: 0, pl: 3, display: 'grid', gap: 1.5 }}>
                        {workflow.map(step => (
                            <Typography component="li" key={step} sx={{ pl: 0.5, lineHeight: 1.7 }}>
                                {step}
                            </Typography>
                        ))}
                    </Box>

                    <Divider sx={{ my: { xs: 4, md: 6 } }} />

                    <Typography component="h2" variant="h4" sx={{ fontWeight: 800, mb: 2 }}>
                        数据与隐私
                    </Typography>
                    <Typography color="text.secondary" sx={{ maxWidth: 820, lineHeight: 1.85 }}>
                        应用仅在用户授权后读取定位、相机和相册，用于工单现场信息采集。业务数据通过认证接口传输并由单位自建服务保存，不用于广告投放或无关用途。
                    </Typography>
                </Container>
            </Box>

            <Box component="footer" sx={{ bgcolor: '#172033', color: '#CBD5E1', py: 3 }}>
                <Container maxWidth="lg">
                    <Typography variant="body2">网络运维管理 · 安卓应用与网页工单协同</Typography>
                </Container>
            </Box>
        </Box>
    );
}

export default AppInfoPage;
