import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Divider,
    List,
    ListItemButton,
    ListItemText,
    MenuItem,
    Stack,
    TextField,
    Typography,
} from '@mui/material';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import PlaceIcon from '@mui/icons-material/Place';
import ContentPasteGoIcon from '@mui/icons-material/ContentPasteGo';

import { loadAmap } from '../map/amapLoader';
import {
    COORDINATE_STANDARD_OPTIONS,
    COORDINATE_STANDARDS,
    distanceMeters,
    formatContractCoordinates,
    formatDistance,
} from '../../utils/coordinateStandards';import { recognizeCoordinate } from '../../services/sessionLocationApiService';

const DEFAULT_CENTER = [117.647, 24.513];

/**
 * 坐标提取器：让无法到达现场的用户也能准确报单。
 *
 * 三条输入路径：地图点选（含拖动微调）、地址搜索、粘贴坐标识别。
 *
 * 关键约束：WGS84 与 GCJ-02 在中国大陆相差数百米，界面必须展示坐标系与换算前后差异，
 * 并在无法确定时要求用户确认。坐标换算统一走服务端，避免前后端算法漂移。
 *
 * @param {object} props
 * @param {{jsApiKey?: string, securityJsCode?: string}} props.mapConfig 后端下发的地图配置
 * @param {(payload: {gpsCoordinates: string, location: string, source: string}) => void} props.onChange 回填回调
 * @param {[number, number]|null} props.initialCenter WGS84 [经度, 纬度]
 */
function CoordinatePicker({ mapConfig, onChange, initialCenter = null, height = 360 }) {
    const containerRef = useRef(null);
    const mapRef = useRef(null);
    const markerRef = useRef(null);
    const geocoderRef = useRef(null);
    const placeSearchRef = useRef(null);

    const [status, setStatus] = useState('loading');
    const [errorMessage, setErrorMessage] = useState('');
    const [pasteText, setPasteText] = useState('');
    const [declaredStandard, setDeclaredStandard] = useState('');
    const [searchKeyword, setSearchKeyword] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [searching, setSearching] = useState(false);
    const [recognizing, setRecognizing] = useState(false);
    const [recognition, setRecognition] = useState(null);
    const [selection, setSelection] = useState(null);
    const [addressDraft, setAddressDraft] = useState('');
    const [resolvingAddress, setResolvingAddress] = useState(false);

    // 拖动标记需要回调到 adopt，而 adopt 又需要移动标记，构成循环。
    // 用 ref 持有最新的 adopt 引用，让 moveMarker 可以保持稳定不被重建。
    const adoptRef = useRef(null);

    const moveMarker = useCallback((longitude, latitude) => {
        const AMap = window.AMap;
        if (!AMap || !mapRef.current) return;
        if (markerRef.current) {
            markerRef.current.setPosition([longitude, latitude]);
        } else {
            markerRef.current = new AMap.Marker({
                position: [longitude, latitude],
                draggable: true,
                cursor: 'move',
                zIndex: 200,
            });
            markerRef.current.on('dragend', (event) => {
                const position = event.lnglat;
                setRecognition(null);
                void adoptRef.current?.(position.getLng(), position.getLat(), {
                    source: 'MAP_PICK',
                    refreshAddress: true,
                });
            });
            mapRef.current.add(markerRef.current);
        }
        mapRef.current.setCenter([longitude, latitude]);
    }, []);

    const resolveAddress = useCallback((longitude, latitude) => new Promise((resolve) => {
        if (!geocoderRef.current) {
            resolve('');
            return;
        }
        setResolvingAddress(true);
        geocoderRef.current.getAddress([longitude, latitude], (result, response) => {
            setResolvingAddress(false);
            resolve(result === 'complete' && response?.regeocode ? response.regeocode.formattedAddress : '');
        });
    }), []);

    /**
     * 统一的坐标采纳入口：写入标记、解析地址、向外回填。
     * 所有输入路径都收敛到这里，避免多处重复的回填逻辑产生不一致。
     */
    const adopt = useCallback(async (gcjLongitude, gcjLatitude, options = {}) => {
        const {
            source = 'MAP_PICK',
            addressHint = '',
            standard = COORDINATE_STANDARDS.GCJ02,
            gpsCoordinates = null,
            wgs84Longitude = null,
            wgs84Latitude = null,
            fallbackAddress = '',
            refreshAddress = false,
        } = options;

        moveMarker(gcjLongitude, gcjLatitude);
        setSelection({ gcjLongitude, gcjLatitude, wgs84Longitude, wgs84Latitude, standard });
        setAddressDraft(fallbackAddress);

        const resolvedCoordinates = gpsCoordinates
            ? { gpsCoordinates, wgs84Longitude, wgs84Latitude }
            : await resolveWgs84(gcjLongitude, gcjLatitude);

        // 已知地址直接采用；地图取点需要重新解析，避免沿用上一处地址。
        const address = addressHint || (refreshAddress || !fallbackAddress
            ? await resolveAddress(gcjLongitude, gcjLatitude)
            : fallbackAddress);
        const finalAddress = address || fallbackAddress;

        setSelection({
            gcjLongitude,
            gcjLatitude,
            wgs84Longitude: resolvedCoordinates.wgs84Longitude,
            wgs84Latitude: resolvedCoordinates.wgs84Latitude,
            standard,
        });
        setAddressDraft(finalAddress);
        onChange?.({
            gpsCoordinates: resolvedCoordinates.gpsCoordinates || '',
            location: finalAddress || '',
            source,
            standard,
        });
    }, [moveMarker, resolveAddress, onChange]);

    useEffect(() => {
        adoptRef.current = adopt;
    }, [adopt]);

    useEffect(() => {
        let cancelled = false;
        setStatus('loading');
        setErrorMessage('');

        loadAmap(mapConfig)
            .then((AMap) => new Promise(resolve => {
                AMap.plugin(['AMap.Geocoder', 'AMap.PlaceSearch'], () => resolve(AMap));
            }))
            .then((AMap) => {
                if (cancelled || !containerRef.current) return;
                const map = new AMap.Map(containerRef.current, {
                    zoom: 13,
                    center: initialCenter ? [...initialCenter] : DEFAULT_CENTER,
                    resizeEnable: true,
                });
                mapRef.current = map;
                geocoderRef.current = new AMap.Geocoder({ city: '全国' });
                // 全国范围搜索：把范围限制在单个城市会让其他地市的用户搜不到任何结果，
                // 且界面无法解释原因。结果相关性由关键词与下方的就近排序保证。
                placeSearchRef.current = new AMap.PlaceSearch({ city: '全国', pageSize: 10 });
                map.on('click', (event) => {
                    const { lng, lat } = event.lnglat;
                    setRecognition(null);
                    void adoptRef.current?.(lng, lat, { source: 'MAP_PICK', refreshAddress: true });
                });
                setStatus('ready');
            })
            .catch((error) => {
                if (cancelled) return;
                setStatus('failed');
                setErrorMessage(error?.message || '地图加载失败，请稍后重试。');
            });

        return () => {
            cancelled = true;
            mapRef.current?.destroy?.();
            mapRef.current = null;
            markerRef.current = null;
        };
        // 仅在密钥变化时重建地图；initialCenter 只作为首次视野。
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mapConfig?.jsApiKey, mapConfig?.securityJsCode]);

    const runSearch = () => {
        if (!searchKeyword.trim() || !placeSearchRef.current) return;
        setSearching(true);
        setSearchResults([]);

        const keyword = searchKeyword.trim();
        const collect = (result, response) => {
            setSearching(false);
            if (result !== 'complete' || !response?.poiList?.pois?.length) {
                setErrorMessage('没有搜索到匹配地点，请换一个关键词或补充城市名。');
                return;
            }
            setErrorMessage('');
            const pois = response.poiList.pois.map(poi => ({
                id: poi.id,
                name: poi.name,
                address: poi.address,
                longitude: poi.location?.lng,
                latitude: poi.location?.lat,
            }));
            // 已在地图上取过点时，把靠近该点的结果排在前面——报单场景下用户要找的通常就在附近。
            const origin = selection?.gcjLongitude != null && selection?.gcjLatitude != null
                ? { longitude: selection.gcjLongitude, latitude: selection.gcjLatitude }
                : null;
            const ordered = origin
                ? [...pois].sort((left, right) => (
                    distanceMeters(origin.latitude, origin.longitude, left.latitude, left.longitude)
                    - distanceMeters(origin.latitude, origin.longitude, right.latitude, right.longitude)
                ))
                : pois;
            setSearchResults(ordered);
        };

        // 以已选点为中心做周边搜索，结果更贴近用户当前关注的位置。
        if (selection?.gcjLongitude != null && selection?.gcjLatitude != null) {
            placeSearchRef.current.searchNearBy(
                keyword,
                [selection.gcjLongitude, selection.gcjLatitude],
                50000,
                (result, response) => {
                    if (result === 'complete' && response?.poiList?.pois?.length) {
                        collect(result, response);
                    } else {
                        // 周边无结果时退回全国搜索，避免因范围过窄而空手而归。
                        placeSearchRef.current.search(keyword, collect);
                    }
                },
            );
            return;
        }
        placeSearchRef.current.search(keyword, collect);
    };

    const recognizePasted = async () => {
        if (!pasteText.trim()) return;
        setRecognizing(true);
        setErrorMessage('');
        try {
            const response = await recognizeCoordinate(pasteText.trim(), declaredStandard || undefined);
            const data = response.data;
            setRecognition(data);
            if (!data?.recognized) {
                setErrorMessage(data?.message || '未能识别坐标，请检查格式。');
                return;
            }
            const preferred = pickPreferredCandidate(data);
            setSelection(null);
            await adopt(preferred.gcj02Longitude, preferred.gcj02Latitude, {
                source: 'COORDINATE_PASTE',
                addressHint: preferred.address || '',
                gpsCoordinates: preferred.gpsCoordinates,
                wgs84Longitude: preferred.wgs84Longitude,
                wgs84Latitude: preferred.wgs84Latitude,
                standard: preferred.standard,
            });
        } catch (error) {
            setErrorMessage(error?.response?.data?.message || '坐标识别失败，请稍后重试。');
        } finally {
            setRecognizing(false);
        }
    };

    const applyCandidate = async (candidate) => {
        setSelection(null);
        await adopt(candidate.gcj02Longitude, candidate.gcj02Latitude, {
            source: 'COORDINATE_PASTE',
            addressHint: candidate.address || '',
            gpsCoordinates: candidate.gpsCoordinates,
            wgs84Longitude: candidate.wgs84Longitude,
            wgs84Latitude: candidate.wgs84Latitude,
            standard: candidate.standard,
        });
    };

    const handleAddressDraftChange = (value) => {
        setAddressDraft(value);
        onChange?.({
            gpsCoordinates: resolvedContract || '',
            location: value,
            source: selection ? 'MAP_PICK' : 'MANUAL',
            standard: selection?.standard,
        });
    };

    // 地图不可用时只降级地图区域：粘贴坐标识别与手动填写不依赖地图，
    // 直接整个组件早退会让用户连这条可用路径也失去。
    const mapUnavailable = status === 'failed';
    const canUseMap = status === 'ready';

    const candidates = recognition?.candidates || [];
    const resolvedContract = selection?.wgs84Longitude && selection?.wgs84Latitude
        ? formatContractCoordinates(selection.wgs84Latitude, selection.wgs84Longitude)
        : '';
    // 用户在多候选间切换后，展示当前选择与识别首选的偏移，帮助判断是否选错坐标系。
    const preferredCandidate = candidates.find(candidate => candidate.preferred) || null;
    const preferredOffset = (() => {
        if (!selection?.wgs84Latitude || !preferredCandidate) return null;
        return distanceMeters(
            selection.wgs84Latitude, selection.wgs84Longitude,
            preferredCandidate.wgs84Latitude, preferredCandidate.wgs84Longitude,
        );
    })();

    return (
        <Stack spacing={2}>
            {mapUnavailable ? (
                <Alert severity="warning">
                    {errorMessage || '地图暂不可用。你仍可粘贴坐标识别，或手动填写经纬度与地址。'}
                </Alert>
            ) : (
                <>
                    <Box sx={{ position: 'relative', height, borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
                        <Box ref={containerRef} sx={{ height, width: '100%' }} />
                        {status === 'loading' && (
                            <Stack alignItems="center" justifyContent="center" spacing={1} sx={{ position: 'absolute', inset: 0, bgcolor: 'background.paper' }}>
                                <CircularProgress size={26} />
                                <Typography variant="body2" color="text.secondary">正在加载地图</Typography>
                            </Stack>
                        )}
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                        在地图上点击取点、拖动标记微调，也可以搜索地址，或粘贴其他地图复制来的坐标。
                    </Typography>
                </>
            )}

            <Stack direction="row" spacing={1} alignItems="flex-start">
                <TextField
                    label="按地址搜索"
                    value={searchKeyword}
                    onChange={(event) => setSearchKeyword(event.target.value)}
                    onKeyDown={(event) => { if (event.key === 'Enter') runSearch(); }}
                    size="small"
                    fullWidth
                    placeholder="例如：漳州市医院"
                />
                <Button
                    onClick={runSearch}
                    disabled={searching || !searchKeyword.trim() || !canUseMap}
                    variant="outlined"
                    sx={{ minWidth: 96, height: 40, flexShrink: 0 }}
                >
                    {searching ? '搜索中' : '搜索'}
                </Button>
            </Stack>
            {searchResults.length > 0 && (
                <List dense sx={{ maxHeight: 200, overflowY: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
                    {searchResults.map(result => (
                        <ListItemButton
                            key={result.id}
                            onClick={() => {
                                setRecognition(null);
                                void adopt(result.longitude, result.latitude, {
                                    source: 'ADDRESS_SEARCH',
                                    addressHint: result.address || result.name,
                                });
                            }}
                        >
                            <ListItemText
                                primary={result.name}
                                secondary={result.address}
                                primaryTypographyProps={{ variant: 'body2' }}
                                secondaryTypographyProps={{ variant: 'caption' }}
                            />
                        </ListItemButton>
                    ))}
                </List>
            )}

            <Divider />

            <Stack direction="row" spacing={1} alignItems="flex-start">
                <TextField
                    label="粘贴坐标"
                    value={pasteText}
                    onChange={(event) => setPasteText(event.target.value)}
                    size="small"
                    fullWidth
                    multiline
                    minRows={1}
                    maxRows={3}
                    placeholder="例如：117.647,24.513 或 lat: 24.513 lng: 117.647"
                />
                <TextField
                    select
                    label="坐标系"
                    value={declaredStandard}
                    onChange={(event) => setDeclaredStandard(event.target.value)}
                    size="small"
                    sx={{ minWidth: 170, flexShrink: 0 }}
                >
                    <MenuItem value="">自动识别</MenuItem>
                    {COORDINATE_STANDARD_OPTIONS.map(option => (
                        <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                    ))}
                </TextField>
                <Button
                    onClick={recognizePasted}
                    disabled={recognizing || !pasteText.trim()}
                    variant="outlined"
                    startIcon={<ContentPasteGoIcon />}
                    sx={{ minWidth: 110, height: 40, flexShrink: 0 }}
                >
                    {recognizing ? '识别中' : '识别'}
                </Button>
            </Stack>

            {(recognition?.message || recognition?.notes?.length > 0) && (
                <Alert severity={recognition?.confident ? 'info' : 'warning'}>
                    {recognition.message}
                    {recognition.notes?.map(note => (
                        <Typography key={note} variant="caption" display="block">{note}</Typography>
                    ))}
                </Alert>
            )}

            {candidates.length > 1 && (
                <Stack spacing={1}>
                    <Typography variant="caption" color="text.secondary">
                        不同坐标系相差可达数百米，请选择与目标地址相符的一项：
                    </Typography>
                    {candidates.map(candidate => (
                        <Stack
                            key={candidate.standard}
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            sx={{ p: 1, border: 1, borderColor: 'divider', borderRadius: 1 }}
                        >
                            <Chip
                                size="small"
                                label={candidate.standardDisplayName}
                                color={candidate.preferred ? 'primary' : 'default'}
                                variant={candidate.preferred ? 'filled' : 'outlined'}
                            />
                            <Typography variant="caption" sx={{ flex: 1 }}>
                                {candidate.address || '该坐标未解析出地址'}
                                {Number.isFinite(candidate.offsetFromPreferredMeters) && candidate.offsetFromPreferredMeters > 1
                                    ? `（与首选相差 ${formatDistance(candidate.offsetFromPreferredMeters)}）`
                                    : ''}
                            </Typography>
                            <Button size="small" onClick={() => applyCandidate(candidate)}>采用</Button>
                        </Stack>
                    ))}
                </Stack>
            )}

            <Divider />

            <Stack spacing={1}>
                <Stack direction="row" spacing={1} alignItems="center">
                    <PlaceIcon fontSize="small" color={resolvedContract ? 'primary' : 'disabled'} />
                    <Typography variant="body2">
                        {resolvedContract
                            ? `已选坐标：${resolvedContract}（WGS84，系统存储标准）`
                            : '尚未选择坐标'}
                    </Typography>
                </Stack>
                {selection?.gcjLongitude && (
                    <Typography variant="caption" color="text.secondary">
                        地图展示坐标（GCJ-02）：{formatContractCoordinates(selection.gcjLatitude, selection.gcjLongitude)}
                    </Typography>
                )}
                {Number.isFinite(preferredOffset) && preferredOffset > 1 && (
                    <Typography variant="caption" color="text.secondary">
                        当前选择与识别首选相差 {formatDistance(preferredOffset)}
                    </Typography>
                )}
                {resolvingAddress && (
                    <Stack direction="row" spacing={1} alignItems="center">
                        <CircularProgress size={14} />
                        <Typography variant="caption" color="text.secondary">正在解析地址</Typography>
                    </Stack>
                )}
                {addressDraft && (
                    <Stack direction="row" spacing={1} alignItems="center">
                        <MyLocationIcon fontSize="small" color="action" />
                        <Typography variant="body2" color="text.secondary">{addressDraft}</Typography>
                    </Stack>
                )}
                <TextField
                    label="工单地点（可修改）"
                    value={addressDraft}
                    onChange={(event) => handleAddressDraftChange(event.target.value)}
                    size="small"
                    fullWidth
                    multiline
                    minRows={1}
                    maxRows={3}
                    placeholder="坐标已选，如有需要可补充或修正详细地点"
                />
            </Stack>

            {errorMessage && status === 'ready' && (
                <Alert severity="warning" onClose={() => setErrorMessage('')}>{errorMessage}</Alert>
            )}
        </Stack>
    );
}

const pickPreferredCandidate = (data) => {
    if (data.confident) {
        return data;
    }
    return data.candidates?.find(candidate => candidate.preferred) || data;
};

/**
 * 地图点选的是 GCJ-02，需要换算回 WGS84 才能进入系统契约。
 * 换算交由服务端统一执行，避免前后端算法漂移。
 */
async function resolveWgs84(gcjLongitude, gcjLatitude) {
    try {
        const response = await recognizeCoordinate(
            formatContractCoordinates(gcjLatitude, gcjLongitude),
            COORDINATE_STANDARDS.GCJ02,
        );
        const data = response.data;
        if (!data?.recognized) {
            return { gpsCoordinates: null, wgs84Longitude: null, wgs84Latitude: null };
        }
        return {
            gpsCoordinates: data.gpsCoordinates,
            wgs84Longitude: data.wgs84Longitude,
            wgs84Latitude: data.wgs84Latitude,
        };
    } catch {
        return { gpsCoordinates: null, wgs84Longitude: null, wgs84Latitude: null };
    }
}

export default CoordinatePicker;
