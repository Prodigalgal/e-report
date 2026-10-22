import contract from '../../../shared/ui-layout.json' with { type: 'json' };

export const uiLayout = Object.freeze(contract);

export const pageGutters = {
    xs: `${uiLayout.pageGutter}px`,
    sm: `${uiLayout.widePageGutter}px`,
};
