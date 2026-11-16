//! Golden serde corpus generator for jaffy-gson.
//!
//! Serializes representative taffy values with the REAL serde impls + serde_json,
//! so the Java side can assert byte-identical output. Writes one .json file per
//! case into jaffy-gson/src/test/resources/golden/ plus an index.txt.

use std::fs;
use std::path::PathBuf;
use taffy::geometry::{FloatInsets, MinMax, Point};
use taffy::prelude::*;
use taffy::style::*;
use taffy::style_helpers::*;
use taffy::tree::*;

fn main() {
    let out = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../jaffy-gson/src/test/resources/golden");
    fs::create_dir_all(&out).unwrap();
    let mut index: Vec<String> = Vec::new();
    let mut emit = |name: &str, json: String| {
        fs::write(out.join(format!("{name}.json")), &json).unwrap();
        index.push(name.to_string());
    };
    macro_rules! case {
        ($name:literal, $v:expr) => {
            emit($name, serde_json::to_string(&$v).unwrap())
        };
    }

    // ---------------- geometry ----------------
    case!("rect_f32", Rect { left: 1.5f32, right: -2.0, top: 3.0, bottom: 0.5 });
    case!("rect_edges", Rect { left: 1e13f32, right: 1e-7, top: -0.0, bottom: 123456789.0 });
    case!("line_f32", Line { start: 1.25f32, end: -0.75 });
    case!("line_bool", Line { start: true, end: false });
    case!("size_bool", Size { width: false, height: true });
    case!("size_f32", Size { width: 10.5f32, height: -0.0 });
    case!("point_f32", Point { x: -3.5f32, y: 2.0 });
    case!("size_opt_f32", Size { width: Some(3.0f32), height: None });
    case!("size_avail", Size { width: AvailableSpace::Definite(10.5), height: AvailableSpace::MaxContent });
    case!("point_overflow", Point { x: Overflow::Hidden, y: Overflow::Scroll });
    case!("float_insets", FloatInsets { left: 1.5, right: -2.25 });
    case!("rect_lpa", Rect { left: LengthPercentageAuto::length(1.0), right: LengthPercentageAuto::auto(),
        top: LengthPercentageAuto::percent(0.5), bottom: LengthPercentageAuto::length(-3.0) });
    case!("size_dim", Size { width: Dimension::length(10.0), height: Dimension::percent(0.5) });
    case!("minmax_tsf", MinMax { min: MinTrackSizingFunction::min_content(), max: MaxTrackSizingFunction::fr(1.5) });

    // ---------------- CompactLength family ----------------
    case!("lp_length", LengthPercentage::length(22.0));
    case!("lp_percent", LengthPercentage::percent(0.5));
    case!("lpa_auto", LengthPercentageAuto::auto());
    case!("lpa_length", LengthPercentageAuto::length(-4.5));
    case!("lpa_percent", LengthPercentageAuto::percent(1.25));
    case!("dim_auto", Dimension::auto());
    case!("dim_length", Dimension::length(1e13));
    case!("dim_percent", Dimension::percent(0.001));
    case!("dim_min_content", Dimension::min_content());
    case!("dim_max_content", Dimension::max_content());
    case!("dim_fit_content", Dimension::fit_content());
    case!("dim_fit_content_px", Dimension::fit_content_px(7.0));
    case!("dim_fit_content_percent", Dimension::fit_content_percent(0.75));
    case!("dim_stretch", Dimension::stretch());
    case!("min_tsf_auto", MinTrackSizingFunction::auto());
    case!("min_tsf_length", MinTrackSizingFunction::length(4.0));
    case!("min_tsf_min_content", MinTrackSizingFunction::min_content());
    case!("max_tsf_fr", MaxTrackSizingFunction::fr(3.0));
    case!("max_tsf_max_content", MaxTrackSizingFunction::max_content());
    case!("max_tsf_fit_content_percent", MaxTrackSizingFunction::fit_content_percent(0.5));
    case!("tsf_minmax", TrackSizingFunction { min: MinTrackSizingFunction::min_content(), max: MaxTrackSizingFunction::fr(2.0) });

    // ---------------- alignment ----------------
    case!("align_items_all", vec![
        AlignItems::NORMAL, AlignItems::START, AlignItems::END, AlignItems::FLEX_START,
        AlignItems::FLEX_END, AlignItems::CENTER, AlignItems::BASELINE, AlignItems::STRETCH,
        AlignItems::SAFE_START, AlignItems::SAFE_END, AlignItems::SAFE_FLEX_START,
        AlignItems::SAFE_FLEX_END, AlignItems::SAFE_CENTER, AlignItems::SELF_START,
        AlignItems::SELF_END, AlignItems::SAFE_SELF_START, AlignItems::SAFE_SELF_END,
        AlignItems::START.with_safety(AlignmentSafety::Unsafe),
        AlignItems::CENTER.with_safety(AlignmentSafety::Unsafe),
        AlignItems::STRETCH.with_safety(AlignmentSafety::Unsafe), // unsafe ignored -> "Stretch"
        AlignItems::NORMAL.with_safety(AlignmentSafety::Safe),    // safe ignored -> "Normal"
    ]);
    case!("align_content_all", vec![
        AlignContent::NORMAL, AlignContent::START, AlignContent::END, AlignContent::FLEX_START,
        AlignContent::FLEX_END, AlignContent::CENTER, AlignContent::STRETCH,
        AlignContent::SPACE_BETWEEN, AlignContent::SPACE_EVENLY, AlignContent::SPACE_AROUND,
        AlignContent::SAFE_START, AlignContent::SAFE_END, AlignContent::SAFE_FLEX_START,
        AlignContent::SAFE_FLEX_END, AlignContent::SAFE_CENTER,
        AlignContent::END.with_safety(AlignmentSafety::Unsafe),
        AlignContent::FLEX_END.with_safety(AlignmentSafety::Unsafe),
        AlignContent::SPACE_AROUND.with_safety(AlignmentSafety::Safe), // safe ignored
    ]);
    case!("align_items_kw_all", vec![
        AlignItemsKeyword::Normal, AlignItemsKeyword::Start, AlignItemsKeyword::End,
        AlignItemsKeyword::FlexStart, AlignItemsKeyword::FlexEnd, AlignItemsKeyword::Center,
        AlignItemsKeyword::Baseline, AlignItemsKeyword::Stretch, AlignItemsKeyword::SelfStart,
        AlignItemsKeyword::SelfEnd,
    ]);
    case!("align_content_kw_all", vec![
        AlignContentKeyword::Normal, AlignContentKeyword::Start, AlignContentKeyword::End,
        AlignContentKeyword::FlexStart, AlignContentKeyword::FlexEnd, AlignContentKeyword::Center,
        AlignContentKeyword::Stretch, AlignContentKeyword::SpaceBetween, AlignContentKeyword::SpaceEvenly,
        AlignContentKeyword::SpaceAround,
    ]);
    case!("safety_all", vec![AlignmentSafety::Default, AlignmentSafety::Safe, AlignmentSafety::Unsafe]);

    // ---------------- simple unit enums ----------------
    case!("display_all", vec![Display::Block, Display::FlowRoot, Display::Flex, Display::Grid, Display::None]);
    case!("position_all", vec![Position::Static, Position::Relative, Position::Absolute, Position::Fixed]);
    case!("box_sizing_all", vec![BoxSizing::ContentBox, BoxSizing::BorderBox]);
    case!("direction_all", vec![Direction::Ltr, Direction::Rtl]);
    case!("overflow_all", vec![Overflow::Visible, Overflow::Clip, Overflow::Hidden, Overflow::Scroll]);
    case!("text_align_all", vec![TextAlign::Auto, TextAlign::LegacyLeft, TextAlign::LegacyRight, TextAlign::LegacyCenter]);
    case!("flex_direction_all", vec![FlexDirection::Row, FlexDirection::RowReverse, FlexDirection::Column, FlexDirection::ColumnReverse]);
    case!("flex_wrap_all", vec![FlexWrap::NoWrap, FlexWrap::Wrap, FlexWrap::WrapReverse, FlexWrap::Balance, FlexWrap::BalanceReverse]);
    case!("float_all", vec![Float::None, Float::Left, Float::Right]);
    case!("clear_all", vec![Clear::None, Clear::Left, Clear::Right, Clear::Both]);
    case!("grid_auto_flow_all", vec![GridAutoFlow::Row, GridAutoFlow::Column, GridAutoFlow::RowDense, GridAutoFlow::ColumnDense]);
    case!("contain", Contain::LAYOUT.union(Contain::PAINT));
    case!("contain_none", Contain::NONE);
    case!("avail_space_all", vec![AvailableSpace::Definite(1.5), AvailableSpace::MinContent, AvailableSpace::MaxContent]);
    case!("run_mode_all", vec![RunMode::PerformLayout, RunMode::ComputeSize, RunMode::PerformHiddenLayout]);
    case!("sizing_mode_all", vec![SizingMode::ContentSize, SizingMode::InherentSize]);
    case!("requested_axis_all", vec![RequestedAxis::Horizontal, RequestedAxis::Vertical, RequestedAxis::Both]);

    // ---------------- grid types ----------------
    // GridLine is pub(crate): public construction goes through the line()/span()
    // helpers. Its wire shape (bare i16) is exercised via {"Line": ...} payloads.
    case!("grid_placement_all", Vec::<GridPlacement>::from([
        GridPlacement::Auto,
        line(3),
        GridPlacement::NamedLine::<String>("main-start".into(), 2),
        span(4),
        GridPlacement::NamedSpan::<String>("sidebar".into(), 2),
    ]));
    case!("line_grid_placement", Line::<GridPlacement> { start: line(-1), end: GridPlacement::Auto });
    case!("repetition_count_all", vec![RepetitionCount::AutoFill, RepetitionCount::AutoFit, RepetitionCount::Count(8)]);
    case!("grid_template_rep", GridTemplateRepetition::<String> {
        count: RepetitionCount::Count(3),
        tracks: vec![
            TrackSizingFunction { min: MinTrackSizingFunction::min_content(), max: MaxTrackSizingFunction::fr(1.0) },
            TrackSizingFunction { min: MinTrackSizingFunction::length(4.0), max: MaxTrackSizingFunction::max_content() },
        ],
        line_names: vec![vec!["a".into(), "b".into()], vec![], vec!["c".into()]],
    });
    case!("grid_template_component_all", vec![
        GridTemplateComponent::<String>::Single(
            TrackSizingFunction { min: MinTrackSizingFunction::min_content(), max: MaxTrackSizingFunction::fr(2.0) }),
        GridTemplateComponent::<String>::Repeat(GridTemplateRepetition::<String> {
            count: RepetitionCount::AutoFill,
            tracks: vec![TrackSizingFunction { min: MinTrackSizingFunction::auto(), max: MaxTrackSizingFunction::fr(1.0) }],
            line_names: vec![vec!["x".into()]],
        }),
    ]);
    case!("grid_template_area", GridTemplateArea::<String> { name: "header".into(), row_start: 1, row_end: 3, column_start: 2, column_end: 5 });
    case!("grid_template_areas", GridTemplateAreas::<String> {
        areas: vec![
            GridTemplateArea::<String> { name: "a".into(), row_start: 1, row_end: 2, column_start: 1, column_end: 2 },
            GridTemplateArea::<String> { name: "b".into(), row_start: 2, row_end: 4, column_start: 2, column_end: 6 },
        ],
        row_count: 4,
        column_count: 6,
    });
    case!("named_grid_line", NamedGridLine::<String> { name: "content-start".into(), index: 3 });

    // ---------------- style ----------------
    case!("style_default", Style::<String>::DEFAULT);
    let loaded = Style::<String> {
        display: Display::Grid,
        item_is_table: true,
        item_is_replaced: true,
        item_is_compressible_replaced: true,
        box_sizing: BoxSizing::ContentBox,
        direction: Direction::Rtl,
        overflow: Point { x: Overflow::Hidden, y: Overflow::Scroll },
        scrollbar_width: 7.5,
        contain: Contain::LAYOUT.union(Contain::PAINT),
        float: Float::Left,
        clear: Clear::Both,
        position: Position::Absolute,
        inset: Rect { left: LengthPercentageAuto::length(1.0), right: LengthPercentageAuto::auto(),
                      top: LengthPercentageAuto::percent(0.1), bottom: LengthPercentageAuto::length(-2.0) },
        size: Size { width: Dimension::length(10.0), height: Dimension::percent(0.5) },
        min_size: Size { width: LengthPercentageAuto::length(1.0), height: LengthPercentageAuto::auto() },
        max_size: Size { width: LengthPercentageAuto::auto(), height: LengthPercentageAuto::percent(0.9) },
        aspect_ratio: Some(1.5),
        margin: Rect { left: LengthPercentageAuto::percent(0.1), right: LengthPercentageAuto::length(2.0),
                       top: LengthPercentageAuto::auto(), bottom: LengthPercentageAuto::length(-1.0) },
        padding: Rect { left: LengthPercentage::length(2.0), right: LengthPercentage::percent(0.05),
                        top: LengthPercentage::length(0.0), bottom: LengthPercentage::length(1.5) },
        border: Rect { left: LengthPercentage::length(0.5), right: LengthPercentage::length(0.5),
                       top: LengthPercentage::length(1.0), bottom: LengthPercentage::length(1.0) },
        align_items: AlignItems::SAFE_CENTER,
        align_self: Some(AlignItems::FLEX_END),
        justify_items: AlignItems::START.with_safety(AlignmentSafety::Unsafe),
        justify_self: Some(AlignItems::BASELINE),
        align_content: AlignContent::SPACE_EVENLY,
        justify_content: AlignContent::FLEX_START,
        gap: Size { width: LengthPercentage::length(4.0), height: LengthPercentage::percent(0.02) },
        text_align: TextAlign::LegacyCenter,
        flex_direction: FlexDirection::RowReverse,
        flex_wrap: FlexWrap::Balance,
        flex_line_count: 2,
        flex_basis: Dimension::percent(0.25),
        flex_grow: 2.5,
        flex_shrink: 0.0,
        grid_template_rows: vec![GridTemplateComponent::<String>::Single(
            TrackSizingFunction { min: MinTrackSizingFunction::min_content(), max: MaxTrackSizingFunction::fr(1.0) })],
        grid_template_columns: vec![GridTemplateComponent::<String>::Repeat(GridTemplateRepetition {
            count: RepetitionCount::Count(2),
            tracks: vec![TrackSizingFunction { min: MinTrackSizingFunction::length(5.0), max: MaxTrackSizingFunction::max_content() }],
            line_names: vec![vec!["col".into()]],
        })],
        grid_auto_rows: vec![TrackSizingFunction { min: MinTrackSizingFunction::auto(), max: MaxTrackSizingFunction::fr(1.0) }],
        grid_auto_columns: vec![],
        grid_auto_flow: GridAutoFlow::ColumnDense,
        grid_template_areas: Some(GridTemplateAreas::<String> {
            areas: vec![GridTemplateArea::<String> { name: "main".into(), row_start: 1, row_end: 2, column_start: 1, column_end: 3 }],
            row_count: 2,
            column_count: 3,
        }),
        grid_template_column_names: vec![vec!["a".into(), "b".into()], vec![]],
        grid_template_row_names: vec![vec![]],
        grid_row: Line { start: line(3), end: span(2) },
        grid_column: Line { start: GridPlacement::NamedLine::<String>("x".into(), 1), end: GridPlacement::Auto },
        ..Style::<String>::DEFAULT
    };
    case!("style_loaded", loaded);
    case!("style_nan", Style {
        scrollbar_width: f32::NAN,
        flex_grow: f32::INFINITY,
        flex_shrink: f32::NEG_INFINITY,
        aspect_ratio: Some(f32::NAN),
        ..Style::<String>::DEFAULT
    });
    case!("style_display_flex", Style { display: Display::Flex, ..Style::<String>::DEFAULT });

    // ---------------- layout (Serialize only) ----------------
    case!("scrollable_overflow", ScrollableOverflow::new(
        Rect { left: -10.0, right: 20.0, top: 0.0, bottom: 0.0 },
        Point { x: true, y: true },
    ));
    case!("collapsible_margin", CollapsibleMarginSet::from_margin(1.5).collapse_with_margin(-0.5));
    case!("baselines", Baselines { first: Some(3.0), last: None });
    case!("oof_positioning_area", OofPositioningArea {
        size: Size { width: 6.0, height: 7.0 },
        offset: Point { x: 1.0, y: 2.0 },
    });
    case!("layout", Layout {
        order: 3,
        location: Point { x: 1.5, y: -2.0 },
        size: Size { width: 10.0, height: 20.0 },
        scrollable_overflow_rect: ScrollableOverflow::new(
            Rect { left: -1.0, right: 2.0, top: 0.0, bottom: 5.0 },
            Point { x: true, y: false }),
        scrollbar_size: Size { width: 0.0, height: 12.0 },
        border: Rect { left: 1.0, right: 1.0, top: 2.0, bottom: 2.0 },
        padding: Rect { left: 0.5, right: 0.5, top: 0.5, bottom: 0.5 },
        margin: Rect { left: 0.0, right: 0.0, top: 0.0, bottom: 0.0 },
    });
    case!("layout_input", LayoutInput {
        run_mode: RunMode::PerformHiddenLayout,
        sizing_mode: SizingMode::InherentSize,
        axis: RequestedAxis::Both,
        known_dimensions: Size { width: None, height: None },
        known_dimensions_are_definite: Size { width: true, height: true },
        parent_size: Size { width: None, height: None },
        available_space: Size { width: AvailableSpace::MaxContent, height: AvailableSpace::MaxContent },
        vertical_margins_are_collapsible: Line { start: false, end: false },
    });
    case!("layout_output", LayoutOutput {
        size: Size { width: 4.0, height: 5.0 },
        scrollable_overflow_rect: ScrollableOverflow::new(
            Rect { left: -5.0, right: 10.0, top: -2.0, bottom: 8.0 },
            Point { x: true, y: false }),
        baselines: Baselines { first: Some(3.0), last: None },
        top_margin: CollapsibleMarginSet::from_margin(1.5).collapse_with_margin(-0.5),
        bottom_margin: CollapsibleMarginSet::ZERO,
        margins_can_collapse_through: true,
        oof_candidates: OofCandidates::NONE,
        oof_positioning_area: Some(OofPositioningArea {
            size: Size { width: 6.0, height: 7.0 },
            offset: Point { x: 1.0, y: 2.0 },
        }),
    });
    case!("layout_output_no_oof_area", LayoutOutput {
        size: Size::ZERO,
        scrollable_overflow_rect: ScrollableOverflow::ZERO,
        baselines: Baselines::NONE,
        top_margin: CollapsibleMarginSet::ZERO,
        bottom_margin: CollapsibleMarginSet::ZERO,
        margins_can_collapse_through: false,
        oof_candidates: OofCandidates::NONE,
        oof_positioning_area: None,
    });

    fs::write(out.join("index.txt"), index.join("\n") + "\n").unwrap();
    println!("wrote {} goldens", index.len());
}
