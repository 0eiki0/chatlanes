package de.chatlanes.mixin;

import de.chatlanes.ModLanguage;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Texte der Mod in der in den Mod-Einstellungen gewählten Sprache anzeigen. */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
	@Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
	private void chatlanes$modLanguage(String key, String defaultValue, CallbackInfoReturnable<String> cir) {
		if (ModLanguage.isModKey(key)) {
			String text = ModLanguage.lookup(key);
			if (text != null) {
				cir.setReturnValue(text);
			}
		}
	}

	@Inject(method = "has", at = @At("HEAD"), cancellable = true)
	private void chatlanes$modLanguageHas(String key, CallbackInfoReturnable<Boolean> cir) {
		if (ModLanguage.isModKey(key) && ModLanguage.lookup(key) != null) {
			cir.setReturnValue(true);
		}
	}
}
