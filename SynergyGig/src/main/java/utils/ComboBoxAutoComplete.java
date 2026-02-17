package utils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class ComboBoxAutoComplete<T> {

    private ComboBox<T> cmb;
    String filter = "";
    private ObservableList<T> originalItems;

    public ComboBoxAutoComplete(ComboBox<T> cmb) {
        this.cmb = cmb;
        this.originalItems = FXCollections.observableArrayList(cmb.getItems());

        cmb.setEditable(true);
        cmb.setOnKeyPressed(this::handleOnKeyPressed);
        cmb.setOnHidden(this::handleOnHiding);
    }

    public void handleOnKeyPressed(KeyEvent e) {
        ObservableList<T> filteredList = FXCollections.observableArrayList();
        KeyCode code = e.getCode();

        if (code.isLetterKey()) {
            filter += e.getText();
        }
        if (code == KeyCode.BACK_SPACE && filter.length() > 0) {
            filter = filter.substring(0, filter.length() - 1);
            cmb.getItems().setAll(originalItems);
        }
        if (code == KeyCode.ESCAPE) {
            filter = "";
        }
        if (filter.length() == 0) {
            filteredList = originalItems;
            cmb.getTooltip().hide();
        } else {
            for (T item : originalItems) {
                if (item.toString().toLowerCase().contains(filter.toLowerCase())) {
                    filteredList.add(item);
                }
            }
            cmb.show();
        }

        // This is a simple implementation.
        // For a more robust one, we might need to listen to textProperty changes
        // instead.
        // But let's try a standard approach using a listener on the editor.
    }

    public void handleOnHiding(javafx.event.Event e) {
        filter = "";
        cmb.getTooltip().hide();
        T s = cmb.getSelectionModel().getSelectedItem();
        cmb.getItems().setAll(originalItems);
        cmb.getSelectionModel().select(s);
    }

    // Static method to auto-configure
    public static <T> void setup(ComboBox<T> comboBox) {
        comboBox.setEditable(true);
        ObservableList<T> items = comboBox.getItems();

        comboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            // If the text change was triggered by selection, don't filter
            if (comboBox.getSelectionModel().getSelectedItem() != null &&
                    comboBox.getSelectionModel().getSelectedItem().toString().equals(newValue)) {
                return;
            }

            if (newValue == null || newValue.isEmpty()) {
                comboBox.setItems(items);
                comboBox.hide();
                return;
            }

            ObservableList<T> filtered = FXCollections.observableArrayList();
            for (T item : items) {
                if (item != null && item.toString().toLowerCase().contains(newValue.toLowerCase())) {
                    filtered.add(item);
                }
            }

            comboBox.setItems(filtered);
            if (!filtered.isEmpty()) {
                comboBox.show();
            } else {
                comboBox.hide();
            }
        });

        // When popup hides, reset items if nothing selected?
        // Or if the user typed exact match
    }
}
