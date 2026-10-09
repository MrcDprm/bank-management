package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.service.HistoryService.MonthTotal;
import com.mrcdprm.bank.service.Session;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

/**
 * Harcama analizi: seçili ayın gelir/gider özeti, giderlerin kategorilere dağılımı (pasta) ve
 * son 6 ayın gelir-gider karşılaştırması (çubuk). Kendi hesapları arası hareketler sayılmaz.
 */
final class InsightsPage extends ScrollPane {

    private static final int MONTHS = 6;

    private final AppContext ctx;
    private final Session session;
    private final List<MonthTotal> months;
    private final HBox tiles = new HBox(14);
    private final PieChart pie = new PieChart();
    private final VBox legend = new VBox(6);

    InsightsPage(AppContext ctx) {
        this.ctx = ctx;
        this.session = ctx.session();
        months = ctx.bank().history().monthly(session, session.userId(), MONTHS);

        final ComboBox<YearMonth> month = new ComboBox<>(FXCollections.observableArrayList(
                months.stream().map(MonthTotal::month).toList().reversed()));
        month.setConverter(new StringConverter<>() {
            @Override
            public String toString(YearMonth m) {
                return m == null ? "" : monthName(m, TextStyle.FULL_STANDALONE) + " " + m.getYear();
            }

            @Override
            public YearMonth fromString(String s) {
                return null;
            }
        });
        month.valueProperty().addListener((obs, old, m) -> show(m));

        pie.setLabelsVisible(false);
        pie.setLegendVisible(false);
        pie.setStartAngle(90);
        pie.setPrefSize(320, 300);
        pie.setMinSize(260, 260);
        final HBox pieRow = new HBox(24, pie, legend);
        pieRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(legend, Priority.ALWAYS);
        final VBox spending = Ui.card(Ui.section(I18n.t("insights.byCategory")), pieRow);

        final VBox trend = Ui.card(Ui.section(I18n.t("insights.trend")), barChart());

        final HBox top = new HBox(Ui.header(I18n.t("nav.insights"), I18n.t("insights.subtitle")), Ui.spacer(), month);
        top.setAlignment(Pos.TOP_LEFT);
        final VBox page = new VBox(18, top, tiles, spending, trend);
        page.setPadding(new Insets(4, 4, 20, 0));
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
        month.getSelectionModel().selectFirst();
    }

    private void show(YearMonth m) {
        if (m == null)
            return;
        final MonthTotal total = months.stream().filter(t -> t.month().equals(m)).findFirst().orElseThrow();
        tiles.getChildren().setAll(
                tile(I18n.t("insights.income"), I18n.money(total.income(), Currency.TRY), "tile-income"),
                tile(I18n.t("insights.expense"), I18n.money(total.expense(), Currency.TRY), "tile-expense"),
                tile(I18n.t("insights.net"), (total.income() >= total.expense() ? "+" : "-")
                        + I18n.money(Math.abs(total.income() - total.expense()), Currency.TRY), "tile-net"));

        final Map<Category, Long> byCategory = ctx.bank().history().spendingByCategory(session, session.userId(), m);
        final long sum = byCategory.values().stream().mapToLong(Long::longValue).sum();
        pie.getData().clear();
        legend.getChildren().clear();
        if (byCategory.isEmpty()) {
            legend.getChildren().add(Ui.muted(I18n.t("insights.noSpending")));
            return;
        }
        int index = 0;
        for (Map.Entry<Category, Long> e : byCategory.entrySet()) {
            final PieChart.Data slice = new PieChart.Data(I18n.of(e.getKey()), e.getValue() / 100.0);
            pie.getData().add(slice);
            final String share = String.format(I18n.locale(), "%.1f", e.getValue() * 100.0 / sum);
            final String text = I18n.of(e.getKey()) + " · " + I18n.money(e.getValue(), Currency.TRY) + " · "
                    + (I18n.isTurkish() ? "%" + share : share + "%");
            Tooltip.install(slice.getNode(), new Tooltip(text));
            final Rectangle swatch = new Rectangle(12, 12);
            swatch.getStyleClass().addAll("legend-swatch", "default-color" + (index % 8));
            final Label name = new Label(I18n.of(e.getKey()));
            final Label amount = new Label(I18n.money(e.getValue(), Currency.TRY));
            amount.getStyleClass().add(Styles.TEXT_BOLD);
            final Label pct = Ui.muted(I18n.isTurkish() ? "%" + share : share + "%");
            final GridPane row = new GridPane();
            row.setHgap(10);
            row.addRow(0, swatch, name, Ui.spacer(), amount, pct);
            GridPane.setHgrow(row.getChildren().get(2), Priority.ALWAYS);
            pct.setMinWidth(48);
            pct.setAlignment(Pos.CENTER_RIGHT);
            legend.getChildren().add(row);
            index++;
        }
    }

    private static VBox tile(String label, String value, String style) {
        final Label v = new Label(value);
        v.getStyleClass().add("tile-value");
        final VBox tile = new VBox(4, Ui.muted(label), v);
        tile.getStyleClass().addAll("panel", "stat-tile", style);
        tile.setPadding(new Insets(14, 18, 14, 18));
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    private BarChart<String, Number> barChart() {
        final CategoryAxis x = new CategoryAxis();
        final NumberAxis y = new NumberAxis();
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number n) {
                return String.format(I18n.locale(), "%,.0f", n.doubleValue());
            }

            @Override
            public Number fromString(String s) {
                return null;
            }
        });
        final BarChart<String, Number> chart = new BarChart<>(x, y);
        final XYChart.Series<String, Number> income = new XYChart.Series<>();
        income.setName(I18n.t("insights.income"));
        final XYChart.Series<String, Number> expense = new XYChart.Series<>();
        expense.setName(I18n.t("insights.expense"));
        for (MonthTotal m : months) {
            final String label = monthName(m.month(), TextStyle.SHORT_STANDALONE);
            income.getData().add(new XYChart.Data<>(label, m.income() / 100.0));
            expense.getData().add(new XYChart.Data<>(label, m.expense() / 100.0));
        }
        chart.getData().addAll(List.of(income, expense));
        chart.setAnimated(false);
        chart.setBarGap(3);
        chart.setCategoryGap(24);
        chart.setPrefHeight(300);
        chart.getStyleClass().add("trend-chart");
        for (XYChart.Series<String, Number> s : chart.getData())
            for (XYChart.Data<String, Number> d : s.getData())
                Tooltip.install(d.getNode(), new Tooltip(s.getName() + ": "
                        + I18n.money(Math.round(d.getYValue().doubleValue() * 100), Currency.TRY)));
        return chart;
    }

    private static String monthName(YearMonth m, TextStyle style) {
        final String name = m.getMonth().getDisplayName(style, I18n.locale());
        return name.substring(0, 1).toUpperCase(I18n.locale()) + name.substring(1);
    }
}
