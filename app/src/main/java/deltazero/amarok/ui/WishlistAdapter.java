package deltazero.amarok.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import deltazero.amarok.R;
import deltazero.amarok.models.WishlistApp;

public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.VH> {

    public interface OnDeleteClickListener {
        void onDelete(WishlistApp app, int position);
    }

    private List<WishlistApp> apps;
    private OnDeleteClickListener deleteListener;

    public WishlistAdapter(List<WishlistApp> apps) {
        this.apps = apps;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void updateData(List<WishlistApp> apps) {
        this.apps = apps;
        notifyDataSetChanged();
    }

    /** Adiciona um item no topo da lista e notifica o adapter */
    public void addItem(WishlistApp app) {
        if (apps == null) apps = new ArrayList<>();
        // Verifica se já existe na lista para evitar duplicata visual
        for (WishlistApp a : apps) {
            if (a.packageName.equals(app.packageName)) return;
        }
        apps.add(0, app);
        notifyItemInserted(0);
    }

    /** Remove item pelo índice */
    public void removeItem(int position) {
        if (apps == null || position < 0 || position >= apps.size()) return;
        apps.remove(position);
        notifyItemRemoved(position);
    }

    /** Atualiza o status de um app específico (durante download) */
    public void updateAppStatus(WishlistApp app) {
        if (apps == null) return;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).packageName.equals(app.packageName)) {
                apps.get(i).status = app.status;
                apps.get(i).errorMessage = app.errorMessage;
                notifyItemChanged(i);
                break;
            }
        }
    }

    /** Retorna apenas os apps com checkbox marcado */
    public List<WishlistApp> getSelectedApps() {
        List<WishlistApp> selected = new ArrayList<>();
        if (apps == null) return selected;
        for (WishlistApp app : apps) {
            if (app.selected) selected.add(app);
        }
        return selected;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wishlist, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        WishlistApp app = apps.get(position);

        holder.tvPackageName.setText(app.packageName);

        // Checkbox — reflete o estado do modelo
        // Remove listener antes de setar para evitar loop
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(app.selected);

        // Desabilita checkbox e lixeira se o app já foi instalado com sucesso ou está em progresso
        boolean isInProgress = app.status.equals(WishlistApp.STATUS_DOWNLOADING)
                || app.status.equals(WishlistApp.STATUS_INSTALLING)
                || app.status.equals(WishlistApp.STATUS_FETCHING_URLS);
        boolean isDone = app.status.equals(WishlistApp.STATUS_SUCCESS);

        holder.cbSelect.setEnabled(!isInProgress);
        holder.btnDelete.setEnabled(!isInProgress);

        // Restaura listener após setar valor
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.selected = isChecked;
        });

        // Visibilidade do status
        if (!app.status.equals(WishlistApp.STATUS_PENDING)) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(statusLabel(app.status));
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        // Cor da borda do card baseada no status
        switch (app.status) {
            case WishlistApp.STATUS_ERROR:
                holder.card.setStrokeColor(Color.parseColor("#D32F2F"));
                holder.card.setStrokeWidth(3);
                holder.tvError.setVisibility(View.VISIBLE);
                String error = app.errorMessage == null || app.errorMessage.isEmpty()
                        ? "Erro sem detalhes salvos. Tente baixar novamente para atualizar o diagnostico."
                        : app.errorMessage;
                holder.tvError.setText(error);
                break;
            case WishlistApp.STATUS_SUCCESS:
                holder.card.setStrokeColor(Color.parseColor("#388E3C"));
                holder.card.setStrokeWidth(3);
                holder.tvError.setVisibility(View.GONE);
                break;
            case WishlistApp.STATUS_INSTALLING:
            case WishlistApp.STATUS_DOWNLOADING:
            case WishlistApp.STATUS_FETCHING_URLS:
                holder.card.setStrokeColor(Color.parseColor("#1976D2"));
                holder.card.setStrokeWidth(3);
                holder.tvError.setVisibility(View.GONE);
                break;
            default:
                holder.card.setStrokeWidth(0);
                holder.tvError.setVisibility(View.GONE);
                break;
        }

        // Botão deletar
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) {
                    deleteListener.onDelete(app, pos);
                }
            }
        });
    }

    private String statusLabel(String status) {
        return switch (status) {
            case WishlistApp.STATUS_FETCHING_URLS -> "🔍 Buscando links...";
            case WishlistApp.STATUS_DOWNLOADING   -> "⬇️ Baixando...";
            case WishlistApp.STATUS_INSTALLING    -> "📦 Instalando...";
            case WishlistApp.STATUS_SUCCESS       -> "✅ Instalado";
            case WishlistApp.STATUS_ERROR         -> "❌ Erro";
            default                               -> "";
        };
    }

    @Override
    public int getItemCount() {
        return apps == null ? 0 : apps.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        CheckBox cbSelect;
        TextView tvPackageName, tvStatus, tvError;
        MaterialButton btnDelete;

        VH(View v) {
            super(v);
            card = v.findViewById(R.id.card_wishlist);
            cbSelect = v.findViewById(R.id.cb_select);
            tvPackageName = v.findViewById(R.id.tv_package_name);
            tvStatus = v.findViewById(R.id.tv_status);
            tvError = v.findViewById(R.id.tv_error);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
