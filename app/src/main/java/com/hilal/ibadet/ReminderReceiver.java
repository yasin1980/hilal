player.prepare();

            player.start();

            handler.postDelayed(
                    finish,
                    8000L
            );

        } catch (Exception ignored) {

            pendingResult.finish();
        }
    }
}
