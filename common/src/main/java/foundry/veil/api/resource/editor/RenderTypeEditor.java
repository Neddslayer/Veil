package foundry.veil.api.resource.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import foundry.veil.Veil;
import foundry.veil.api.client.imgui.VeilImGuiUtil;
import foundry.veil.api.client.render.rendertype.layer.CompositeRenderTypeData;
import foundry.veil.api.client.render.rendertype.layer.RenderTypeLayer;
import foundry.veil.api.client.util.VertexFormatCodec;
import foundry.veil.api.resource.VeilEditorEnvironment;
import foundry.veil.api.resource.VeilResourceInfo;
import foundry.veil.api.resource.VeilResourceManager;
import foundry.veil.api.resource.type.RenderTypeResource;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RenderTypeEditor implements ResourceFileEditor<RenderTypeResource> {

    private static final Component TITLE = Component.translatable("inspector.veil.render_type.title");
    private final ImBoolean open;
    private final VeilResourceManager resourceManager;
    private final RenderTypeResource resource;

    private RenderTypeDefinitionBuilder renderType;

    public RenderTypeEditor(VeilEditorEnvironment environment, RenderTypeResource resource) {
        this.open = new ImBoolean(true);
        this.resourceManager = environment.getResourceManager();
        this.resource = resource;
        this.loadFromDisk();
    }

    @Override
    public void render() {
        if (this.resource == null || this.renderType == null) {
            return;
        }

        VeilResourceInfo resourceInfo = this.resource.resourceInfo();
        ImGui.setNextWindowSizeConstraints(256.0F, 256.0F, Float.MAX_VALUE, Float.MAX_VALUE);
        ImGui.setNextWindowSize(256.0F, 256.0F, ImGuiCond.Once);
        if (ImGui.begin(TITLE.getString() + "###render_type_" + resourceInfo.fileName(), this.open, ImGuiWindowFlags.NoSavedSettings)) {
            Map<String, VertexFormat> formats = VertexFormatCodec.getDefaultFormats();
            ImInt selectedFormat = new ImInt(formats.values().stream().toList().indexOf(this.renderType.getFormat()));
            if (ImGui.combo("format", selectedFormat, formats.keySet().toArray(String[]::new))) {
                this.renderType.setFormat(formats.get(formats.keySet().toArray(String[]::new)[selectedFormat.get()]));
                this.save();
            }

            VertexFormat.Mode[] modes = VertexFormat.Mode.values();
            ImInt selectedMode = new ImInt(List.of(modes).indexOf(this.renderType.getMode()));
            if (ImGui.combo("mode", selectedMode, Arrays.stream(modes).map(Enum::name).toArray(String[]::new))) {
                this.renderType.setMode(modes[selectedMode.get()]);
                this.save();
            }

            ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.4f);
            ImInt editBufferSize = new ImInt(this.renderType.getBufferSize());
            if (ImGui.inputInt("bufferSize", editBufferSize)) {
                this.renderType.setBufferSize(editBufferSize.get());
                this.save();
            }
            ImGui.sameLine();
            if (ImGui.button("TRANSIENT")) {
                this.renderType.setBufferSize(RenderType.TRANSIENT_BUFFER_SIZE);
                this.save();
            }
            ImGui.sameLine();
            if (ImGui.button("SMALL")) {
                this.renderType.setBufferSize(RenderType.SMALL_BUFFER_SIZE);
                this.save();
            }
            ImGui.sameLine();
            if (ImGui.button("BIG")) {
                this.renderType.setBufferSize(RenderType.BIG_BUFFER_SIZE);
                this.save();
            }
            ImGui.popItemWidth();

            ImBoolean editAffectsCrumbling = new ImBoolean(this.renderType.affectsCrumbling());
            if (ImGui.checkbox("affectsCrumbling", editAffectsCrumbling)) {
                this.renderType.setAffectsCrumbling(editAffectsCrumbling.get());
                this.save();
            }

            ImBoolean editSort = new ImBoolean(this.renderType.sort());
            if (ImGui.checkbox("sort", editSort)) {
                this.renderType.setSort(editSort.get());
                this.save();
            }

            ImBoolean editOutline = new ImBoolean(this.renderType.outline());
            if (ImGui.checkbox("outline", editOutline)) {
                this.renderType.setOutline(editOutline.get());
                this.save();
            }

            ImGui.separator();

            ImGui.text("Layers");
        }
        ImGui.end();
    }

    @Override
    public void loadFromDisk() {
        try (BufferedReader reader = this.resource.resourceInfo().openAsReader(this.resourceManager)) {
            DataResult<CompositeRenderTypeData> result = CompositeRenderTypeData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
            if (result.error().isPresent()) {
                throw new JsonParseException(result.error().get().message());
            }
            this.renderType = new RenderTypeDefinitionBuilder(result.getOrThrow());
        } catch (IOException e) {
            Veil.LOGGER.error("Failed to open resource: {}", this.resource.resourceInfo().location(), e);
        }

    }

    private void save() {
        try {
            CompositeRenderTypeData data = this.renderType.build();
            DataResult<JsonElement> result = CompositeRenderTypeData.CODEC.encodeStart(JsonOps.INSTANCE, data);
            if (result.error().isPresent()) {
                throw new JsonSyntaxException(result.error().get().message());
            }

            this.save(result.getOrThrow(), this.resourceManager, this.resource);
        } catch (Exception e) {
            Veil.LOGGER.error("Failed to save resource: {}", this.resource.resourceInfo().location(), e);
        }
    }

    @Override
    public boolean isClosed() {
        return !this.open.get();
    }

    @Override
    public RenderTypeResource getResource() {
        return this.resource;
    }

    private static class RenderTypeDefinitionBuilder {
        private VertexFormat format;
        private VertexFormat.Mode mode;
        private int bufferSize;
        private boolean affectsCrumbling;
        private boolean sort;
        private boolean outline;
        private List<List<RenderTypeLayer>> layers;

        public RenderTypeDefinitionBuilder(CompositeRenderTypeData definition) {
            this.format = definition.format();
            this.mode = definition.mode();
            this.bufferSize = definition.bufferSize();
            this.affectsCrumbling = definition.affectsCrumbling();
            this.sort = definition.sort();
            this.outline = definition.outline();
            this.layers = new ArrayList<>();
            for (RenderTypeLayer[] layerArray : definition.layers()) {
                layers.add(new ArrayList<>(List.of(layerArray)));
            }
        }

        public VertexFormat getFormat() {
            return format;
        }

        public RenderTypeDefinitionBuilder setFormat(VertexFormat format) {
            this.format = format;
            return this;
        }

        public VertexFormat.Mode getMode() {
            return mode;
        }

        public RenderTypeDefinitionBuilder setMode(VertexFormat.Mode mode) {
            this.mode = mode;
            return this;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public RenderTypeDefinitionBuilder setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public boolean affectsCrumbling() {
            return affectsCrumbling;
        }

        public RenderTypeDefinitionBuilder setAffectsCrumbling(boolean affectsCrumbling) {
            this.affectsCrumbling = affectsCrumbling;
            return this;
        }

        public boolean sort() {
            return sort;
        }

        public RenderTypeDefinitionBuilder setSort(boolean sort) {
            this.sort = sort;
            return this;
        }

        public boolean outline() {
            return outline;
        }

        public RenderTypeDefinitionBuilder setOutline(boolean outline) {
            this.outline = outline;
            return this;
        }

        public List<List<RenderTypeLayer>> getLayers() {
            return layers;
        }

        public RenderTypeDefinitionBuilder setLayers(List<List<RenderTypeLayer>> layers) {
            this.layers = layers;
            return this;
        }

        public CompositeRenderTypeData build() {
            return new CompositeRenderTypeData(this.format, this.mode, this.bufferSize, this.affectsCrumbling, this.sort, this.outline, this.layers.stream().map(list -> list.toArray(RenderTypeLayer[]::new)).toList());
        }
    }
}
