package store

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"easyaiot/terminal/backend/session"
)

func TestCurrentPresetsRefreshLegacyBrandWithoutChangingUserConnections(t *testing.T) {
	for _, name := range []string{legacyMiddlewareBrand + " 中间件", legacyMiddlewareBrand + " 中間件", legacyMiddlewareBrand + " Middleware", "My middleware"} {
		t.Run(name, func(t *testing.T) {
			cs, dir := newTestConnectionStore(t)
			preset := MiddlewarePresets()[0]
			preset.Host = "custom-host.example"
			preset.Remark = strings.ReplaceAll(preset.Remark, "yFeiEye", legacyMiddlewareBrand)
			custom := session.ConnectionConfig{ID: "my-connection", Name: "My connection", Remark: legacyMiddlewareBrand + " custom note"}
			original := session.ConnectionStoreData{
				Groups:      []session.ConnectionGroup{{ID: MiddlewarePresetGroupID, Name: name}},
				Connections: []session.ConnectionConfig{preset, custom},
			}
			if err := cs.Save(original); err != nil {
				t.Fatal(err)
			}
			marker, err := json.Marshal(middlewarePresetMarkerData{Version: MiddlewarePresetsVersion})
			if err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(filepath.Join(dir, middlewarePresetsMarker), marker, 0644); err != nil {
				t.Fatal(err)
			}
			if changed, err := EnsureMiddlewarePresets(dir, cs, "zh-CN"); err != nil || !changed {
				t.Fatalf("branding refresh: changed=%v err=%v", changed, err)
			}
			actual, err := cs.Load()
			if err != nil {
				t.Fatal(err)
			}
			expected := original
			if name != "My middleware" {
				expected.Groups[0].Name = "yFeiEye 中间件"
			}
			expected.Connections[0].Remark = MiddlewarePresets()[0].Remark
			if !reflect.DeepEqual(actual, expected) {
				t.Fatal("branding refresh changed connection data or did not preserve custom labels")
			}
			if changed, err := EnsureMiddlewarePresets(dir, cs, "zh-CN"); err != nil || changed {
				t.Fatalf("repeat refresh: changed=%v err=%v", changed, err)
			}
		})
	}
}
